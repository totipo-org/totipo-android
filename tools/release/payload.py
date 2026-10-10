"""Compare APK ZIP payloads, excluding only file offsets/signing envelopes."""
import hashlib
import struct
import zipfile
from pathlib import Path


def require(condition, message):
    if not condition:
        raise ValueError(message)


def inventory(path):
    data = Path(path).read_bytes()
    with zipfile.ZipFile(path) as archive:
        require(archive.testzip() is None, 'Corrupt ZIP')
        entries = archive.infolist()
        require(len(entries) == len({e.filename for e in entries}), 'Duplicate ZIP names')
        result = []
        for e in entries:
            require(not (e.filename.upper().startswith('META-INF/') and
                         e.filename.upper().endswith(('.RSA', '.DSA', '.EC', '.SF'))),
                    'JAR signature payload forbidden')
            # Compare central metadata and raw local headers (which contain no offsets).
            fields = ('filename', 'orig_filename', 'date_time', 'compress_type', 'comment',
                      'extra', 'create_system', 'create_version', 'extract_version',
                      'flag_bits', 'volume', 'internal_attr', 'external_attr',
                      'CRC', 'compress_size', 'file_size')
            offset = e.header_offset
            require(data[offset:offset + 4] == b'PK\x03\x04', 'Invalid local header')
            name_size, extra_size = struct.unpack_from('<HH', data, offset + 26)
            header_end = offset + 30 + name_size + extra_size
            compressed_end = header_end + e.compress_size
            result.append((tuple(getattr(e, f) for f in fields),
                           data[offset:header_end],
                           hashlib.sha256(data[header_end:compressed_end]).hexdigest(),
                           hashlib.sha256(archive.read(e)).hexdigest()))
        return archive.comment, result


def compare(unsigned, signed):
    require(inventory(unsigned) == inventory(signed), 'APK ZIP payload differs')


if __name__ == '__main__':
    import argparse
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('unsigned')
    parser.add_argument('signed')
    args = parser.parse_args()
    compare(args.unsigned, args.signed)
    print('Identical APK ZIP payload, metadata, order and compressed bytes')
