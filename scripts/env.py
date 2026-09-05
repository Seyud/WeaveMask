#!/usr/bin/env python3
import functools
import multiprocessing
import os
import platform
import sys
import shutil
import socket
import subprocess
from pathlib import Path
from typing import NoReturn


__all__ = [
    "ondk_version",
    "color_print",
    "error",
    "header",
    "Paths",
    "paths",
    "run_once",
    "ensure_toolchain",
    "ensure_cargo",
    "ensure_jdk",
    "os_name",
    "is_windows",
    "EXE_EXT",
    "no_color",
    "cpu_count",
]

ondk_version = "r30.1"


def color_print(code, msg):
    if no_color:
        print(msg)
    else:
        msg = msg.replace("\n", f"\033[0m\n{code}")
        print(f"{code}{msg}\033[0m")


def error(msg) -> NoReturn:
    color_print("\033[41;39m", f"\n! {msg}\n")
    sys.exit(1)


def header(msg):
    color_print("\033[44;39m", f"\n{msg}\n")


class Paths:
    def __init__(self):
        try:
            self.sdk = Path(os.environ["ANDROID_HOME"])
        except KeyError:
            try:
                self.sdk = Path(os.environ["ANDROID_SDK_ROOT"])
            except KeyError:
                error(
                    "Please set Android SDK path to environment variable ANDROID_HOME"
                )

        self.ndk = self.sdk / "ndk" / "magisk"
        self.ndk_build = self.ndk / "ndk-build"
        self.rust_sysroot = self.ndk / "toolchains" / "rust"
        self.adb = self.sdk / "platform-tools" / "adb"
        project_root = Path(__file__).resolve().parent.parent
        self.gradlew = project_root / "app" / "gradlew"


@functools.cache
def paths() -> Paths:
    return Paths()


def run_once(func):
    @functools.wraps(func)
    def wrapper(*args, **kwargs):
        if not wrapper.has_run:
            wrapper.has_run = True
            return func(*args, **kwargs)

    wrapper.has_run = False
    return wrapper


def _read_config_prop():
    props = {}
    prop_file = Path(__file__).resolve().parent.parent / "config.prop"
    if prop_file.exists():
        for line in prop_file.read_text().splitlines():
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            if "=" in line:
                key, _, value = line.partition("=")
                props[key.strip()] = value.strip()
    return props


@run_once
def ensure_toolchain():
    # Verify NDK install
    try:
        with open(paths().ndk / "ONDK_VERSION", "r") as ondk_ver:
            assert ondk_ver.read().strip(" \t\r\n") == ondk_version
    except:
        error('Unmatched NDK. Please install/upgrade NDK with "build.py ndk"')

    # Use sccache for both Rust and C/C++ by default
    if sccache := shutil.which("sccache"):
        os.environ["RUSTC_WRAPPER"] = sccache
        os.environ["NDK_CCACHE"] = sccache
        os.environ["CARGO_INCREMENTAL"] = "0"

        props = _read_config_prop()
        if "SCCACHE_SERVER_PORT" not in os.environ:
            if port := props.get("sccachePort", ""):
                os.environ["SCCACHE_SERVER_PORT"] = port
            elif is_windows:
                # sccache's default port (4226) is commonly reserved by
                # Hyper-V/WSL on Windows, causing startup to fail with
                # WSAEACCES (os error 10013). Pick an available local TCP
                # port instead.
                with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
                    sock.bind(("127.0.0.1", 0))
                    os.environ["SCCACHE_SERVER_PORT"] = str(sock.getsockname()[1])
        if "SCCACHE_DIR" not in os.environ:
            if cache_dir := props.get("sccacheDir", ""):
                os.environ["SCCACHE_DIR"] = cache_dir

    # Prefer ccache over sccache for NDK builds when both are installed
    if ccache := shutil.which("ccache"):
        os.environ["NDK_CCACHE"] = ccache


@run_once
def ensure_cargo():
    ensure_toolchain()

    os.environ["CARGO_BUILD_RUSTFLAGS"] = f"-Z threads={min(8, cpu_count)}"
    if shutil.which("rustup"):
        # Go through rustup proxies by default if available
        os.environ["RUSTUP_TOOLCHAIN"] = str(paths().rust_sysroot)
    else:
        os.environ["PATH"] = (
            f"{paths().rust_sysroot / 'bin'}{os.pathsep}{os.environ['PATH']}"
        )
        # Cargo calls executables in $RUSTROOT/lib/rustlib/$TRIPLE/bin, we need
        # to make sure the runtime linker also search $RUSTROOT/lib for libraries.
        # This is only required on Unix, as Windows search dlls from PATH.
        rust_lib = str(paths().rust_sysroot / "lib")
        if os_name == "darwin":
            cur = os.environ.get("DYLD_FALLBACK_LIBRARY_PATH")
            os.environ["DYLD_FALLBACK_LIBRARY_PATH"] = (
                f"{rust_lib}{os.pathsep}{cur}" if cur else rust_lib
            )
        elif os_name == "linux":
            cur = os.environ.get("LD_LIBRARY_PATH")
            os.environ["LD_LIBRARY_PATH"] = (
                f"{rust_lib}{os.pathsep}{cur}" if cur else rust_lib
            )

    # On Windows, the rustup proxy launches ONDK cargo.exe from a different
    # working directory, causing DLLs like libLLVM-21.dll and libc++.dll in
    # the ONDK bin directory to not be found. Always add the ONDK bin to PATH
    # so the runtime linker can locate these DLLs.
    if is_windows:
        os.environ["PATH"] = (
            f"{paths().rust_sysroot / 'bin'}{os.pathsep}{os.environ['PATH']}"
        )


@run_once
def ensure_jdk():
    if "ANDROID_STUDIO" in os.environ:
        studio = os.environ["ANDROID_STUDIO"]
        jbr = Path(studio, "jbr")
        if not jbr.exists():
            jbr = Path(studio, "Contents", "jbr", "Contents", "Home")
        if jbr.exists():
            os.environ["PATH"] = f'{jbr / "bin"}{os.pathsep}{os.environ["PATH"]}'
            os.environ["JAVA_HOME"] = str(jbr)
            os.environ["JDK_HOME"] = str(jbr)

    no_jdk = False
    try:
        proc = subprocess.run(
            "javac -version",
            stdout=subprocess.PIPE,
            stderr=subprocess.DEVNULL,
            shell=True,
        )
        output = proc.stdout.strip().decode("utf-8")
        no_jdk = proc.returncode != 0 or not output.startswith("javac 21")
    except FileNotFoundError:
        no_jdk = True

    if no_jdk:
        error(
            "Please set Android Studio's path to environment variable ANDROID_STUDIO,\n"
            + "or install JDK 21 and make sure 'javac' is available in PATH"
        )


# OS detection
os_name = platform.system().lower()
is_windows = False
if os_name != "linux" and os_name != "darwin":
    # It's possible we're using MSYS/Cygwin/MinGW, treat them all as Windows
    is_windows = True
    os_name = "windows"
EXE_EXT = ".exe" if is_windows else ""

no_color = "NO_COLOR" in os.environ or not sys.stdout.isatty()
if is_windows and not no_color:
    try:
        import colorama

        colorama.init()
    except ImportError:
        # We can't do ANSI color codes in terminal on Windows without colorama
        no_color = True

# Environment detection
if not sys.version_info >= (3, 8):
    error("Requires Python 3.8+")

cpu_count = multiprocessing.cpu_count()

# When directly invoked, make it a command wrapper
if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: env.py <command> [args...]")
        sys.exit(1)
    ensure_cargo()
    ensure_jdk()
    proc = subprocess.run(sys.argv[1:])
    sys.exit(proc.returncode)
