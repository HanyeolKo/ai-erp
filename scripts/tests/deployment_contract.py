"""Run actual deployment scripts against a strict, stateful host boundary.

Mandatory call omissions change ordered effects, state or command count and fail.
Linux uses real flock and POSIX paths; Windows limitations are explicitly reported.
"""
import json
import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
from concurrent.futures import ThreadPoolExecutor

PROJECT = Path(sys.argv[1])
if os.name == 'nt' and str(PROJECT).startswith('\\'):
    PROJECT = Path(subprocess.check_output(['D:/Git/bin/bash.exe', '-c', 'cygpath -w "$1"', '_', sys.argv[1]], text=True).strip())
BASH = 'D:/Git/bin/bash.exe' if os.name == 'nt' else shutil.which('bash')
A, B, C, D = (x * 40 for x in 'abcd')
assertions = scenarios = 0

def check(value, message):
    global assertions
    assertions += 1
    if not value:
        raise AssertionError(message)

def posix(path):
    return str(path).replace('\\', '/')

class Host:
    def __init__(self, root):
        self.root, self.host, self.bin = root, root / 'host', root / 'bin'
        self.data, self.log = root / 'runtime.json', root / 'calls.jsonl'
        self.bin.mkdir()
        for d in (self.host, self.host / 'shared', self.host / 'shared/caddy', self.host / 'releases'):
            d.mkdir(exist_ok=True)
            d.chmod(0o700)
        self.envfile = self.host / 'shared/.env'
        self.envfile.write_text('''POSTGRES_DB=ai_erp
POSTGRES_USER=ai_erp
POSTGRES_PASSWORD=do-not-print-database-secret
DB_USERNAME=ai_erp
DB_PASSWORD=do-not-print-database-secret
DB_URL=jdbc:postgresql://postgres:5432/ai_erp
REDIS_PASSWORD=do-not-print-redis-secret-123
REDIS_URL=redis://:do-not-print-redis-secret-123@redis:6379/0
SESSION_SECRET=do-not-print-session-secret
APP_OIDC_ENABLED=false
GOOGLE_CLIENT_ID=
GOOGLE_CLIENT_SECRET=
SITE_ADDRESS=192.168.219.100
''', encoding='utf-8')
        self.envfile.chmod(0o600)
        upstream = self.host / 'shared/caddy/active-upstream.caddy'
        upstream.write_text('respond "service initializing" 503\n', encoding='utf-8')
        upstream.chmod(0o600)
        runtime = PROJECT / 'scripts/tests/fake-runtime.py'
        for command in ('docker', 'curl', 'git', 'stat', 'readlink', 'ss', 'df', 'mv', 'ln'):
            self.wrapper(command, f'exec "{posix(sys.executable)}" "{posix(runtime)}" {command} "$@"')
        self.wrapper('python3', f'exec "{posix(sys.executable)}" "$@"')
        if os.name == 'nt':
            self.wrapper('flock', '[[ "$*" == "-n 9" ]] || exit 97\n[[ "${FAKE_LOCK_BUSY:-0}" != 1 ]]')
            self.wrapper('stat', '''[[ $# == 3 && "$1" == -c && ( "$2" == %a || "$2" == %u ) && -e "$3" ]] || exit 97
if [[ "$2" == %a ]]; then
  if [[ "${FAKE_BAD_MODE:-0}" == 1 ]]; then echo 777; elif [[ -d "$3" ]]; then echo 700; else echo 600; fi
else
  if [[ "${FAKE_BAD_OWNER:-0}" == 1 ]]; then echo 99999; else /usr/bin/stat -c %u "$3"; fi
fi''')
            self.wrapper('readlink', '''[[ $# == 2 && "$1" == -f ]] || exit 97
if [[ "${FAKE_SYMLINK:-0}" == 1 && "$2" == "$AI_ERP_ROOT" ]]; then echo /untrusted/alias; exit; fi
result=$(/usr/bin/readlink -f "$2")
if [[ "$result" =~ ^/([A-Za-z])/(.*)$ ]]; then
  drive=${BASH_REMATCH[1]}; printf '%s:/%s\\n' "${drive^^}" "${BASH_REMATCH[2]}"
else printf '%s\\n' "$result"; fi''')
        self.env = dict(os.environ, PATH=posix(self.bin) + os.pathsep + os.environ['PATH'],
                        AI_ERP_ROOT=posix(self.host), AI_ERP_PROJECT_DIR=posix(PROJECT),
                        AI_ERP_TEST_MODE='1', AI_ERP_AVAILABLE_MEM_KIB='3000000',
                        OBSERVATION_SECONDS='0', HEALTH_ATTEMPTS='1', HEALTH_INTERVAL_SECONDS='0',
                        FAKE_DATA=posix(self.data), FAKE_LOG=posix(self.log), FAKE_HEAD=A,
                        MSYS_NO_PATHCONV='1', MSYS2_ARG_CONV_EXCL='*')
        self.save({'images': {}, 'containers': {}, 'resources': {}, 'history': False, 'live': ''})
        self.output = ''
        self.entrypoints = PROJECT / 'scripts'

    def wrapper(self, name, body):
        file = self.bin / name
        file.write_text('#!/usr/bin/env bash\nset -Eeuo pipefail\n' + body + '\n', encoding='utf-8')
        file.chmod(0o755)

    def save(self, data):
        self.data.write_text(json.dumps(data), encoding='utf-8')

    def runtime(self):
        return json.loads(self.data.read_text(encoding='utf-8'))

    def calls(self):
        return [json.loads(x) for x in self.log.read_text(encoding='utf-8').splitlines()] if self.log.exists() else []

    def run(self, command, release=None, success=True, **extra):
        global scenarios
        scenarios += 1
        case = scenarios
        if command == 'deploy':
            runtime = self.runtime()
            runtime['backed_up'] = False
            self.save(runtime)
        env = dict(self.env, **extra)
        if release in (A, B, C, D):
            env['FAKE_HEAD'] = release
        setup = 'export PATH="$(cygpath -u "$1"):$PATH"; shift; exec bash "$@"' if os.name == 'nt' else 'export PATH="$1:$PATH"; shift; exec bash "$@"'
        args = [BASH, '-c', setup, '_',
                posix(self.bin), posix(self.entrypoints / (command + '.sh'))]
        if release is not None:
            args.append(release)
        result = subprocess.run(args, env=env, text=True, capture_output=True)
        print(f'case {case}: {command} {extra.get("FAKE_FAIL", "")} exit={result.returncode}', flush=True)
        self.output += result.stdout + result.stderr
        check((result.returncode == 0) == success,
              f'{command} {release}: expected success={success}, got {result.returncode}\n{result.stdout}{result.stderr}')
        check(result.returncode in (0, 1, 2), f'unexpected process failure: {result.returncode}\n{result.stdout}{result.stderr}')
        return result

    def state(self):
        return json.loads((self.host / 'shared/active-state.json').read_text(encoding='utf-8'))

    def snapshot(self):
        return ((self.host / 'shared/caddy/active-upstream.caddy').read_bytes(),
                (self.host / 'shared/active-state.json').read_bytes() if (self.host / 'shared/active-state.json').exists() else None)

    def preserved(self, snapshot):
        check(self.snapshot() == snapshot, 'abort must restore exact upstream and state, including absence')
        check(not list(self.host.rglob('*.candidate')) and not list(self.host.rglob('*.previous')), 'stale transaction files')

    def clean(self):
        payload = self.output + self.log.read_text(encoding='utf-8')
        for p in self.host.rglob('*'):
            if p.is_file() and p.name != '.env' and p.suffix != '.dump':
                payload += p.read_text(encoding='utf-8')
        check('do-not-print' not in payload, 'secret leaked to output, calls or durable metadata')
        check(not any(x['event'] in ('delete', 'prune', 'foreign-stop', 'unsupported') for x in self.calls()), 'unsafe or unsupported runtime operation')

def ordered(events, expected):
    index = -1
    for stage in expected:
        index = next((i for i in range(index + 1, len(events)) if events[i] == stage), -1)
        check(index >= 0, 'missing/out-of-order effect ' + stage + ': ' + ', '.join(events))

def clone(host, name):
    path = host.root / name
    path.mkdir()
    child = Host(path)
    shutil.copytree(host.host, child.host, dirs_exist_ok=True)
    child.save(host.runtime())
    return child

def linux_lock_checks(h):
    if os.name == 'nt':
        return
    env = dict(h.env)
    script = 'exec 9>>"$AI_ERP_ROOT/shared/deploy.lock"; flock -n 9; echo LOCKED; read -r release'
    holder = subprocess.Popen([BASH, '-c', script], env=env, stdin=subprocess.PIPE, stdout=subprocess.PIPE, text=True)
    try:
        check(holder.stdout.readline().strip() == 'LOCKED', 'real flock holder acquired exact lock')
        h.run('preflight', C, success=False, AI_ERP_LOCK_HELD='1')
        h.run('deploy', C, success=False, AI_ERP_LOCK_HELD='1')
        script = 'exec 9>>"$1"; exec bash "$2" "$3"'
        result = subprocess.run([BASH, '-c', script, '_', posix(h.root / 'arbitrary.lock'), posix(PROJECT / 'scripts/preflight.sh'), C], env=dict(env, FAKE_HEAD=C), capture_output=True)
        check(result.returncode != 0, 'arbitrary inherited fd must not bypass competing real flock')
    finally:
        holder.communicate('release\n', timeout=10)
    script = 'exec 9>>"$AI_ERP_ROOT/shared/deploy.lock"; flock -n 9; exec bash "$1" "$2"'
    result = subprocess.run([BASH, '-c', script, '_', posix(PROJECT / 'scripts/preflight.sh'), C], env=dict(env, FAKE_HEAD=C), capture_output=True)
    check(result.returncode == 0, 'genuine inherited exact fd must re-flock and succeed')

def integrity_checks(h):
    def corrupt(kind):
        child = clone(h, 'corrupt-' + kind)
        manifest = child.host / f'releases/{C}/manifest.json'
        runtime = child.runtime()
        if kind == 'manifest-missing':
            manifest.unlink()
        elif kind == 'manifest-tampered':
            manifest.write_text(manifest.read_text().replace('"migrationResult":"success"', '"migrationResult":"failed"'))
        elif kind == 'image-missing':
            del runtime['images']['sha256:' + C + '0' * 24]
        elif kind == 'image-label':
            runtime['images']['sha256:' + C + '0' * 24][1] = B
        elif kind == 'image-tag':
            runtime['images']['ai-erp:' + C][0] = 'sha256:' + 'f' * 64
        elif kind == 'container-missing':
            del runtime['containers']['app-blue']
        elif kind == 'container-image':
            runtime['containers']['app-blue']['image'] = 'sha256:' + 'f' * 64
        elif kind == 'upstream':
            (child.host / 'shared/caddy/active-upstream.caddy').write_text(f'header X-AI-ERP-Release "{C}"\nreverse_proxy app-green:8080\n')
        elif kind == 'transaction':
            file = child.host / 'shared/caddy/state.previous'
            file.write_text('{}')
            file.chmod(0o600)
        elif kind == 'state-json':
            (child.host / 'shared/active-state.json').write_text('{"releaseId":"bad", "releaseId":"duplicate"}')
        elif kind == 'previous-manifest':
            previous = child.state()['previousRelease']
            (child.host / f'releases/{previous}/manifest.json').unlink()
        child.save(runtime)
        child.run('preflight', C, success=False)
        check(not any(x['event'].startswith('start:') for x in child.calls()), 'untrusted state must not recreate a slot')
    with ThreadPoolExecutor(max_workers=4) as pool:
        list(pool.map(corrupt, ('manifest-missing', 'manifest-tampered', 'image-missing', 'image-label', 'image-tag',
                               'container-missing', 'container-image', 'upstream', 'transaction', 'state-json', 'previous-manifest')))
    if os.name != 'nt':
        child = clone(h, 'real-symlink')
        env_file = child.envfile
        original = env_file.with_name('original-env')
        env_file.rename(original)
        env_file.symlink_to(original)
        child.run('preflight', C, success=False)
        child = clone(h, 'real-mode')
        child.envfile.chmod(0o644)
        child.run('preflight', C, success=False)

def mutation_checks(h):
    # Each mutation runs the real deploy entrypoint and then applies the same
    # required effect predicates as the positive first-deploy contract.
    def mutate(item):
        name, needle, required = item
        child = clone(h, 'mutation-' + name)
        child.entrypoints = child.root / 'scripts'
        child.entrypoints.mkdir()
        for p in (PROJECT / 'scripts').glob('*.sh'):
            shutil.copy2(p, child.entrypoints / p.name)
        file = child.entrypoints / 'deploy.sh'
        lines = file.read_text(encoding='utf-8').splitlines()
        removed = [line for line in lines if needle in line]
        check(len(removed) == 1, 'mutation must alter exactly one required call')
        file.write_text('\n'.join(line for line in lines if needle not in line) + '\n', encoding='utf-8')
        file.chmod(0o755)
        child.run('deploy', C)
        events = [x['event'] for x in child.calls()]
        check(required not in events, 'missing required call must fail the positive contract effect assertion: ' + name)
    with ThreadPoolExecutor(max_workers=4) as pool:
        list(pool.map(mutate, [('migration', '--profile migration run', 'migrate'),
                              ('backup', '/backup.sh"', 'backup'),
                              ('internal-smoke', '/smoke.sh" internal', 'internal:/')]))

def suite(root):
    h = Host(root)
    if os.environ.get('CONTRACT_RED_PROBE') == '1':
        runtime = h.runtime()
        runtime['containers']['postgres'] = dict(running=True, image='infra', release='')
        h.save(runtime)
        h.run('backup', A)
        check(sum(x['event'] == 'backup' for x in h.calls()) == 1, 'first empty database must produce a validated backup; skipping it is unsafe')
        return
    h.run('deploy', A)
    events = [x['event'] for x in h.calls()]
    check(events.count('backup') == 1, 'first deploy must back up empty database before Flyway')
    check(events.count('migrate') == 1, 'exactly one external Flyway invocation')
    ordered(events, ['build', 'healthy:postgres', 'healthy:redis', 'healthy:caddy', 'backup', 'backup-validate',
                     'git-clean', 'migrate', 'start:app-blue', 'identity:app-blue', 'internal:/',
                     'caddy-validate', 'switch', 'public:/', 'public:/'])
    check(h.state()['releaseId'] == A and h.state()['color'] == 'blue', 'first active state')
    backups = list((h.host / f'releases/{A}/backups').glob('*.json'))
    check(len(backups) == 1, 'first deployment has one atomic backup metadata file')
    metadata = json.loads(backups[0].read_text())
    check(metadata['historyExisted'] == 'f', 'empty DB history is recorded without skipping backup')
    check(metadata['dumpChecksum'] == hashlib.sha256(backups[0].with_suffix('.dump').read_bytes()).hexdigest(), 'backup checksum matches actual dump')
    for key in ('composeChecksum', 'caddyChecksum', 'migrationChecksum'):
        check(len(metadata[key]) == 64, 'backup has config/migration checksum: ' + key)
    a_manifest = (h.host / f'releases/{A}/manifest.json').read_bytes()
    h.run('deploy', B)
    check(h.state()['color'] == 'green' and h.state()['previousRelease'] == A, 'A -> B lineage')
    checkpoint = len(h.calls())
    h.run('deploy', B)
    check(not any(x['event'] in ('build', 'migrate', 'backup', 'switch') for x in h.calls()[checkpoint:]), 'same active SHA smoke-only no-op')
    h.run('deploy', A, success=False)
    before = h.snapshot()
    def failpoint(point):
        child = clone(h, 'fail-' + point)
        child.run('deploy', C, success=False, FAKE_FAIL=point)
        child.preserved(before)
        check(not any(x['event'] == 'unsupported' for x in child.calls()), 'unknown fake command at ' + point)
        expected = {'candidate-health': 'start:app-blue', 'candidate-image': 'identity:app-blue',
                    'internal': 'identity:app-blue', 'reload': 'caddy-validate',
                    'public': 'switch', 'public-second': 'public:/', 'manifest': 'public:/', 'state': 'file-install:manifest.json',
                    'manifest-checksum': 'file-install:manifest.json', 'upstream': 'caddy-validate', 'backup-metadata': 'backup-validate'}
        check(any(x['event'] == expected.get(point, point) for x in child.calls()), 'failpoint was not reached: ' + point)
    with ThreadPoolExecutor(max_workers=4) as pool:
        list(pool.map(failpoint, ('build', 'infra', 'backup', 'backup-validate', 'probe', 'migrate', 'candidate-health',
                                'candidate-image', 'internal', 'caddy-validate', 'reload', 'public', 'public-second', 'manifest', 'manifest-checksum', 'state', 'upstream', 'backup-metadata')))
    mutation_checks(h)
    h.run('rollback')
    check(h.state()['releaseId'] == A and h.state()['color'] == 'blue' and h.state()['previousRelease'] == B, 'no-arg rollback A in inactive blue')
    h.run('rollback')
    check(h.state()['releaseId'] == B and h.state()['previousRelease'] == A, 'second no-arg rollback B')
    check((h.host / f'releases/{A}/manifest.json').read_bytes() == a_manifest, 'rollback preserves immutable manifest')
    h.run('deploy', D)
    before = h.snapshot()
    h.run('rollback', A, success=False, FAKE_FAIL='public')
    h.preserved(before)
    h.run('rollback', A)
    check(h.state()['color'] == 'green' and h.state()['previousRelease'] == D, 'explicit older rollback current inactive slot')
    for bad in ('A' * 40, '../bad', '', 'a' * 39, 'a' * 41):
        h.run('deploy', bad, success=False)
        h.run('rollback', bad if bad else '../bad', success=False)
    for extra in ({'FAKE_DIRTY': ' M tracked'}, {'FAKE_DIRTY': '?? untracked'}, {'FAKE_WRONG_HEAD': B},
                  {'FAKE_DIRTY_LATE': '1'}, {'FAKE_PORT': 'host'}, {'FAKE_PORT': 'foreign'},
                  {'FAKE_RESOURCE': 'foreign'}, {'FAKE_RESOURCE': 'unlabelled'},
                  {'FAKE_BAD_MODE': '1'}, {'FAKE_BAD_OWNER': '1'}, {'FAKE_SYMLINK': '1'}):
        h.run('deploy', C, success=False, **extra)
    if os.name == 'nt':
        h.run('deploy', C, success=False, FAKE_LOCK_BUSY='1', AI_ERP_LOCK_HELD='1')
    env_original = h.envfile.read_text(encoding='utf-8')
    for old, new in [('SITE_ADDRESS=192.168.219.100', 'SITE_ADDRESS=other'),
                     ('DB_URL=jdbc:postgresql://postgres:5432/ai_erp', 'DB_URL=jdbc:postgresql://foreign:5432/ai_erp'),
                     ('DB_USERNAME=ai_erp', 'DB_USERNAME=foreign'),
                     ('REDIS_URL=redis://:do-not-print-redis-secret-123@redis:6379/0', 'REDIS_URL=redis://foreign:6379/0'),
                     ('APP_OIDC_ENABLED=false', 'APP_OIDC_ENABLED=true'),
                     ('GOOGLE_CLIENT_ID=', 'GOOGLE_CLIENT_ID=unpaired')]:
        h.envfile.write_text(env_original.replace(old, new), encoding='utf-8')
        h.run('preflight', C, success=False)
    h.envfile.write_text(env_original + 'APP_IMAGE=foreign:latest\n', encoding='utf-8')
    h.run('deploy', C)
    check(h.state()['releaseId'] == C, 'hostile APP_IMAGE cannot change candidate identity')
    h.envfile.write_text(env_original, encoding='utf-8')
    state_path = h.host / 'shared/active-state.json'
    saved_state = state_path.read_bytes()
    state_path.unlink()
    h.run('preflight', C, success=False)
    state_path.write_bytes(saved_state)
    state_path.chmod(0o600)
    for key, value in [('releaseId', B), ('imageId', 'sha256:' + 'f' * 64), ('revision', B), ('previousColor', 'bad')]:
        state = json.loads(saved_state)
        state[key] = value
        state_path.write_text(json.dumps(state), encoding='utf-8')
        h.run('preflight', C, success=False)
    state_path.write_bytes(saved_state)
    linux_lock_checks(h)
    integrity_checks(h)
    h.clean()

with tempfile.TemporaryDirectory(prefix='ai-erp-contract-') as temp:
    try:
        suite(Path(temp))
        for point in ('reload', 'public', 'manifest', 'state'):
            path = Path(temp) / point
            path.mkdir()
            h = Host(path)
            before = h.snapshot()
            h.run('deploy', A, success=False, FAKE_FAIL=point)
            h.preserved(before)
            h.run('deploy', A)
            check(h.state()['releaseId'] == A, 'first deployment retry')
            h.clean()
    except Exception:
        print(f'FAIL after {scenarios} scenarios / {assertions} assertions', file=sys.stderr)
        raise
print(f'PASS: {scenarios} scenarios / {assertions} assertions')
if os.name == 'nt':
    print('LIMIT: Linux flock contention and real POSIX ownership/mode/symlinks require Linux; Windows uses explicit command doubles.')
