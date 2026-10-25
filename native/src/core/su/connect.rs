use super::SuInfo;
use crate::consts::{APP_PACKAGE_NAME, INTERNAL_DIR, MAGISK_FILE_CON};
use crate::daemon::{AID_APP_START, MagiskD, to_app_id, to_user_id};
use crate::ffi::{SuPolicy, SuRequest, fork_dont_care, get_magisk_tmp};
use crate::socket::IpcRead;
use ExtraVal::{Bool, Int, IntList, Str};
use base::const_format::concatcp;
use base::{
    BytesExt, FileAttr, LibcReturn, LoggedResult, ResultExt, Utf8CStrBuf, cstr, error,
};
use nix::fcntl::OFlag;
use nix::poll::{PollFd, PollFlags, PollTimeout};
use nix::sys::signal::SigSet;
use num_traits::AsPrimitive;
use std::fmt::Write as _;
use std::fs::File;
use std::os::fd::AsFd;
use std::os::unix::net::UCred;
use std::os::unix::process::CommandExt;
use std::process::{Command, exit};

fn app_process() -> Command {
    let mut cmd = Command::new("/system/bin/app_process");
    // daemon thread blocks all signals, unblock it before starting app_process
    unsafe {
        cmd.pre_exec(|| SigSet::empty().thread_set_mask().map_err(Into::into));
    }
    cmd
}

fn timeout_app_process() -> Command {
    let mut cmd = Command::new("/system/bin/timeout");
    // daemon thread blocks all signals, unblock it before starting app_process
    unsafe {
        cmd.pre_exec(|| SigSet::empty().thread_set_mask().map_err(Into::into));
    }
    cmd
}

struct Extra<'a> {
    key: &'static str,
    value: ExtraVal<'a>,
}

enum ExtraVal<'a> {
    Int(i32),
    Bool(bool),
    Str(&'a str),
    IntList(&'a [u32]),
}

impl Extra<'_> {
    fn add_bind(&self, cmd: &mut Command) {
        let mut tmp: String;
        match self.value {
            Int(i) => {
                tmp = format!("{}:i:{}", self.key, i);
            }
            Bool(b) => {
                tmp = format!("{}:b:{}", self.key, b);
            }
            Str(s) => {
                let s = s.replace("\\", "\\\\").replace(":", "\\:");
                tmp = format!("{}:s:{}", self.key, s);
            }
            IntList(list) => {
                tmp = format!("{}:s:", self.key);
                if !list.is_empty() {
                    list.iter().for_each(|i| {
                        write!(&mut tmp, "{i},").ok();
                    });
                    tmp.pop();
                }
            }
        }
        cmd.args(["--extra", &tmp]);
    }

    fn add_bind_legacy(&self, cmd: &mut Command) {
        match self.value {
            Str(s) => {
                let tmp = format!("{}:s:{}", self.key, s);
                cmd.args(["--extra", &tmp]);
            }
            _ => self.add_bind(cmd),
        }
    }

    fn add_intent(&self, cmd: &mut Command) {
        match self.value {
            Int(i) => {
                cmd.args(["--ei", self.key, &i.to_string()]);
            }
            Bool(b) => {
                cmd.args(["--ez", self.key, &b.to_string()]);
            }
            Str(s) => {
                cmd.args(["--es", self.key, s]);
            }
            IntList(list) => {
                cmd.args(["--es", self.key]);
                let mut tmp = String::new();
                list.iter().for_each(|i| {
                    write!(&mut tmp, "{i},").ok();
                });
                tmp.pop();
                cmd.arg(&tmp);
            }
        }
    }
}

impl MagiskD {
    fn exec_cmd(&self, user: &str, action: &'static str, extras: &[Extra]) {
        // Wrap with `timeout -k`: if the manager process is frozen (cached-app
        // freezer) or killed mid-flight, the synchronous binder reply never
        // arrives and `.output()` below would block forever, leaking one
        // ~150MB orphaned app_process per su session. Bound it hard:
        // SIGTERM at 20s, SIGKILL 5s later.
        let mut cmd = timeout_app_process();
        cmd.args([
            "/system/bin",
            "com.android.commands.content.Content",
            "call",
            "--uri",
            concatcp!("content://", APP_PACKAGE_NAME, ".provider"),
            "--user",
            user,
            "--method",
            action,
        ]);
        if self.sdk_int() >= 30 {
            extras.iter().for_each(|e| e.add_bind(&mut cmd))
        } else {
            extras.iter().for_each(|e| e.add_bind_legacy(&mut cmd))
        }
        cmd.env("CLASSPATH", "/system/framework/content.jar");

        match cmd.output() {
            Ok(output) => {
                if output.stderr.contains(b"Error") || output.stdout.contains(b"Error") {
                    error!(
                        "content call failed: {}",
                        String::from_utf8_lossy(&output.stderr)
                    );
                }
            }
            Err(e) => {
                error!("failed to execute content call: {e}");
            }
        }
    }

    fn app_notify(&self, cred: &UCred, info: &SuInfo, request: &SuRequest) {
        self.notify_impl(
            cred,
            info,
            request,
            info.settings.policy.repr,
            info.settings.log,
            info.settings.notify,
        );
    }

    // Fork: report the outcome of an interactive request. The UID had no
    // policy row, so the regular notify path would skip the su log entry.
    pub(super) fn notify_request_result(
        &self,
        cred: &UCred,
        info: &SuInfo,
        request: &SuRequest,
        policy: SuPolicy,
    ) {
        if fork_dont_care() != 0 {
            return;
        }

        self.notify_impl(cred, info, request, policy.repr, true, true);

        exit(0);
    }

    fn notify_impl(
        &self,
        cred: &UCred,
        info: &SuInfo,
        request: &SuRequest,
        policy_repr: i32,
        log: bool,
        notify: bool,
    ) {
        let mut command = request.command.join(" ");
        let mut legacy_cmd = false;
        if request.command.len() >= 4
            && request.command[1] == "-c"
            && !request.command[2].contains(b" ")
        {
            command = format!("(Syntax Error) {}", command);
            legacy_cmd = true;
        }
        let extras = [
            Extra {
                key: "from.uid",
                value: Int(cred.uid.as_()),
            },
            Extra {
                key: "to.uid",
                value: Int(request.target_uid),
            },
            Extra {
                key: "pid",
                value: Int(cred.pid.unwrap_or(-1).as_()),
            },
            Extra {
                key: "policy",
                value: Int(policy_repr),
            },
            Extra {
                key: "target",
                value: Int(request.target_pid),
            },
            Extra {
                key: "context",
                value: Str(&request.context),
            },
            Extra {
                key: "gids",
                value: IntList(&request.gids),
            },
            Extra {
                key: "command",
                value: Str(&command),
            },
            Extra {
                key: "legacy_cmd",
                value: Bool(legacy_cmd),
            },
            Extra {
                key: "notify",
                value: Bool(notify),
            },
            Extra {
                key: "log",
                value: Bool(log),
            },
        ];
        let user_id;
        let user = if to_app_id(info.eval_uid) < AID_APP_START {
            "current"
        } else {
            user_id = to_user_id(info.eval_uid).to_string();
            &user_id
        };
        self.exec_cmd(user, "notify", &extras);
    }

    // Fork: interactive su request flow removed upstream in v31.1, restored
    // for blacklist mode. Launches the manager's request activity and waits
    // on a FIFO for the user's decision (persisted app-side).
    pub(super) fn app_request(&self, cred: &UCred, info: &SuInfo, _request: &SuRequest) -> SuPolicy {
        let mut fifo = cstr::buf::new::<64>();
        fifo.write_fmt(format_args!(
            "{}/{}/su_request_{}",
            get_magisk_tmp(),
            INTERNAL_DIR,
            cred.pid.unwrap_or(-1)
        ))
        .ok();

        let fd = || -> LoggedResult<File> {
            let mut attr = FileAttr::new();
            attr.st.st_mode = 0o600;
            let mgr_uid = self.get_manager_uid(to_user_id(info.eval_uid));
            if mgr_uid >= 0 {
                attr.st.st_uid = mgr_uid.as_();
                attr.st.st_gid = mgr_uid.as_();
            }
            attr.con.push_str(MAGISK_FILE_CON);

            fifo.mkfifo(0o600)?;
            fifo.set_attr(&attr)?;

            let extras = [
                Extra {
                    key: "fifo",
                    value: Str(&fifo),
                },
                Extra {
                    key: "uid",
                    value: Int(info.eval_uid),
                },
                Extra {
                    key: "pid",
                    value: Int(cred.pid.unwrap_or(-1)),
                },
            ];
            let user_id;
            let user = if to_app_id(info.eval_uid) < AID_APP_START {
                "current"
            } else {
                user_id = to_user_id(info.eval_uid).to_string();
                &user_id
            };
            self.start_request(user, &extras);

            // Open with O_RDWR to prevent FIFO open block
            let fd = fifo.open(OFlag::O_RDWR | OFlag::O_CLOEXEC)?;
            let mut pfd = [PollFd::new(fd.as_fd(), PollFlags::POLLIN)];

            // Wait for data input for at most 70 seconds
            nix::poll::poll(
                &mut pfd,
                PollTimeout::try_from(70 * 1000).unwrap_or(PollTimeout::NONE),
            )
            .check_os_err("poll", None, None)?;
            Ok(fd)
        }();

        fifo.remove().log_ok();

        if let Ok(mut fd) = fd {
            SuPolicy {
                repr: fd
                    .read_decodable::<i32>()
                    .log()
                    .map(i32::from_be)
                    .unwrap_or(SuPolicy::Deny.repr),
            }
        } else {
            SuPolicy::Deny
        }
    }

    fn start_request(&self, user: &str, extras: &[Extra]) {
        let mut cmd = app_process();
        cmd.args([
            "/system/bin",
            "com.android.commands.am.Am",
            "start",
            "-p",
            APP_PACKAGE_NAME,
            "--user",
            user,
            "-a",
            "android.intent.action.VIEW",
            "-f",
            // FLAG_ACTIVITY_NEW_TASK|FLAG_ACTIVITY_MULTIPLE_TASK|
            // FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS|FLAG_INCLUDE_STOPPED_PACKAGES
            "0x18800020",
            "--es",
            "action",
            "request",
        ]);
        extras.iter().for_each(|e| e.add_intent(&mut cmd));
        cmd.env("CLASSPATH", "/system/framework/am.jar");

        // Async start activity
        if fork_dont_care() == 0 {
            let err = cmd.exec();
            error!("exec app_process: {err}");
            exit(1);
        }
    }

    pub(super) fn notify_app(&self, cred: &UCred, info: &SuInfo, request: &SuRequest) {
        if !info.settings.log && !info.settings.notify {
            return;
        }

        if fork_dont_care() != 0 {
            return;
        }

        // Notify su usage to application
        self.app_notify(cred, info, request);

        exit(0);
    }
}
