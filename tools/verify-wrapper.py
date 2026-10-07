#!/usr/bin/env python3
"""Verify existing wrapper inputs without writing any trusted state."""
import hashlib
from pathlib import Path

root = Path(__file__).resolve().parent.parent
expected_jar = '238e777fcddd7e34f9708186085def2abd6e08e658505b38718d79d74c21abd5'
assert hashlib.sha256((root / 'gradle/wrapper/gradle-wrapper.jar').read_bytes()).hexdigest() == expected_jar, 'Wrapper JAR checksum mismatch'
props = dict(line.split('=', 1) for line in (root / 'gradle/wrapper/gradle-wrapper.properties').read_text().splitlines() if '=' in line)
assert props['distributionUrl'] == r'https\://services.gradle.org/distributions/gradle-9.8.0-bin.zip', 'Unexpected wrapper distribution'
assert props['distributionSha256Sum'] == 'bafd5ce9cfaea0fbccfdc8439a1ac42fbd4cd9c89dc9a988228d8a2639a58e6c', 'Unexpected distribution checksum'
assert props['validateDistributionUrl'] == 'true'
print('Verified Gradle 9.8.0 wrapper JAR and distribution pins')
