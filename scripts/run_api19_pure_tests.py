#!/usr/bin/env python3
"""Offline JVM regression subset using existing tools. This does not build or validate an APK."""
import argparse
from pathlib import Path
import os
import subprocess

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--java-home', type=Path, required=True)
parser.add_argument('--cache-root', type=Path, required=True,
                    help='Existing Gradle caches/modules-2/files-2.1 directory (read only)')
parser.add_argument('--android-jar', type=Path, help='Optional existing SDK stub jar for compile-only platform checks')
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
output = root / 'build' / 'api19-verification'
output.mkdir(parents=True, exist_ok=True)

def jar(group, artifact, version):
    matches = list((args.cache_root / group / artifact / version).glob(f'*/{artifact}-{version}.jar'))
    if len(matches) != 1:
        raise SystemExit(f'Missing or ambiguous cached dependency: {group}:{artifact}:{version}. No download attempted.')
    return str(matches[0])

stdlib = jar('org.jetbrains.kotlin', 'kotlin-stdlib', '2.0.21')
annotations = jar('org.jetbrains', 'annotations', '13.0')
compiler = [
    jar('org.jetbrains.kotlin', 'kotlin-compiler-embeddable', '2.0.21'), stdlib,
    jar('org.jetbrains.kotlin', 'kotlin-script-runtime', '2.0.21'),
    jar('org.jetbrains.kotlin', 'kotlin-reflect', '1.6.10'),
    jar('org.jetbrains.intellij.deps', 'trove4j', '1.0.20200330'),
    jar('org.jetbrains.kotlinx', 'kotlinx-coroutines-core-jvm', '1.6.4'), annotations,
]
runtime = [stdlib, annotations, jar('junit', 'junit', '4.13.2'),
           jar('org.hamcrest', 'hamcrest-core', '1.3')]
java = str(args.java_home / 'bin' / ('java.exe' if os.name == 'nt' else 'java'))
names = ['AudioBufferProgress', 'MediaCodecStartup', 'MediaFailureSummary', 'MediaCodecBufferWindow']
sources = [root / 'shared' / 'src' / kind / 'java' / 'com' / 'shilapi' / 'xcertplay' / 'media' /
           (name + ('Test' if kind == 'test' else '') + '.kt') for name in names for kind in ['main', 'test']]
test_classes = ['com.shilapi.xcertplay.media.' + name + 'Test' for name in names]
for name in ['LegacyUsbDescriptors', 'UsbTransferPolicy']:
    sources.extend(root / 'shared' / 'src' / kind / 'java/com/shilapi/xcertplay/transport' /
                   (name + ('Test' if kind == 'test' else '') + '.kt') for kind in ['main', 'test'])
    test_classes.append('com.shilapi.xcertplay.transport.' + name + 'Test')
sources.extend(root / 'common' / 'src' / kind / 'java/com/shilapi/xcertplay' /
               ('LegacyDisplayDefaults' + ('Test' if kind == 'test' else '') + '.kt') for kind in ['main', 'test'])
test_classes.append('com.shilapi.xcertplay.LegacyDisplayDefaultsTest')
compiled = output / 'pure-tests.jar'

def run(command, log):
    result = subprocess.run(command, cwd=root, text=True, stdout=subprocess.PIPE,
                            stderr=subprocess.STDOUT, encoding='utf-8', errors='replace')
    (output / log).write_text(result.stdout, encoding='utf-8')
    print(result.stdout)
    if result.returncode:
        raise SystemExit(result.returncode)

run([java, '-cp', os.pathsep.join(compiler), 'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler',
     '-no-stdlib', '-no-reflect', '-jvm-target', '11', '-classpath', os.pathsep.join(runtime),
     '-d', str(compiled), *map(str, sources)], 'pure-compile.log')
run([java, '-cp', os.pathsep.join([str(compiled), *runtime]), 'org.junit.runner.JUnitCore',
     *test_classes], 'pure-junit.log')
print('PASS: offline JVM subset only; Android compilation, API19 runtime, lint and APK installation were not exercised.')
if args.android_jar:
    platform_names = ['LegacyAudioFallback', 'AudioChannelMapping']
    platform_sources = [root / 'shared' / 'src' / kind / 'java' / 'com' / 'shilapi' / 'xcertplay' / 'media' /
        (name + ('Test' if kind == 'test' else '') + '.kt') for name in platform_names for kind in ['main', 'test']]
    platform_sources.append(root / 'shared/src/main/java/com/shilapi/xcertplay/media/MediaPlatformCompat.kt')
    platform_sources.append(root / 'common/src/main/java/com/shilapi/xcertplay/DeviceCapabilityReport.kt')
    platform_jar = output / 'platform-smoke.jar'
    classpath = os.pathsep.join([*runtime, str(args.android_jar)])
    run([java, '-cp', os.pathsep.join(compiler), 'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler',
         '-no-stdlib', '-no-reflect', '-jvm-target', '11', '-classpath', classpath,
         '-d', str(platform_jar), *map(str, platform_sources)], 'platform-compile.log')
    run([java, '-cp', os.pathsep.join([str(platform_jar), classpath]), 'org.junit.runner.JUnitCore',
         *['com.shilapi.xcertplay.media.' + name + 'Test' for name in platform_names]], 'mapping-junit.log')
    print('PASS: platform helper type-checks against the supplied SDK; mapping/fallback JVM tests passed. No Android runtime exercised.')
