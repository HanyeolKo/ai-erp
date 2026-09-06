"""Strict production boundary double: every unsupported call fails closed."""
import json
import hashlib
import os
from pathlib import Path
import re
import subprocess
import sys
from urllib.parse import urlencode, urlsplit

command, *args = sys.argv[1:]
data_file, log_file = Path(os.environ['FAKE_DATA']), Path(os.environ['FAKE_LOG'])
data = json.loads(data_file.read_text(encoding='utf-8'))
root = Path(os.environ['AI_ERP_ROOT'])
point = os.environ.get('FAKE_FAIL', '')

def event(name, **extra):
    with log_file.open('a', encoding='utf-8') as f:
        f.write(json.dumps(dict(event=name, **extra)) + '\n')

def save():
    data_file.write_text(json.dumps(data), encoding='utf-8')

def die(message='unsupported command/arguments', code=97):
    if code == 97:
        event('unsupported', boundary=command)
    print('fake-runtime: ' + message, file=sys.stderr)
    sys.exit(code)

def output(value):
    sys.stdout.reconfigure(newline='\n')
    print(value)
    sys.exit(0)

def fault(name):
    if point == name:
        die('injected ' + name, 1)

def native(name, values):
    executable = f'D:/Git/usr/bin/{name}.exe' if os.name == 'nt' else '/usr/bin/' + name
    sys.exit(subprocess.call([executable, *values]))

def response_body(path, container):
    if path == '/actuator/health/readiness':
        return '{"status":"UP"}'
    if path == '/api/v1/system/configuration':
        enabled = container.get('oidc_enabled', False)
        mismatch = os.environ.get('FAKE_CONFIGURATION', '')
        if mismatch == 'malformed':
            return 'ok'
        if mismatch in ('enabled', 'disabled'):
            enabled = mismatch == 'enabled'
        login_url = '/oauth2/authorization/google' if enabled else None
        if mismatch == 'bad-login-url':
            login_url = 'https://foreign.test/login'
        return json.dumps(dict(login='READY' if enabled else 'CONFIGURATION_REQUIRED', loginUrl=login_url))
    return 'ok'


if command == 'git':
    if len(args) < 3 or args[:2] != ['-C', os.environ['AI_ERP_PROJECT_DIR']]:
        die()
    tail = args[2:]
    if tail in (['rev-parse', 'HEAD'], ['rev-parse', '--verify', 'HEAD^{commit}']):
        output(os.environ.get('FAKE_WRONG_HEAD', os.environ['FAKE_HEAD']))
    if tail == ['status', '--porcelain=v1', '--untracked-files=all']:
        event('git-clean')
        output('?? late-untracked' if os.environ.get('FAKE_DIRTY_LATE') and data.get('backed_up') else os.environ.get('FAKE_DIRTY', ''))
    die()
if command == 'stat':
    if len(args) != 3 or args[0] != '-c' or args[1] not in ('%a', '%u'):
        die()
    p = Path(args[2])
    if not p.exists():
        die('missing stat path', 1)
    if args[1] == '%a':
        if os.environ.get('FAKE_BAD_MODE'):
            output('777')
        if os.name == 'nt':
            output('700' if p.is_dir() else '600')
    elif os.environ.get('FAKE_BAD_OWNER'):
        output('99999')
    native(command, args)
if command == 'readlink':
    if len(args) != 2 or args[0] != '-f':
        die()
    if os.environ.get('FAKE_SYMLINK') and args[1] == os.environ['AI_ERP_ROOT']:
        output('/untrusted/alias')
    if os.name == 'nt':
        result = subprocess.run(['D:/Git/usr/bin/readlink.exe', *args], text=True, capture_output=True)
        value = result.stdout.strip()
        if re.match(r'^/[a-zA-Z]/', value):
            value = value[1].upper() + ':' + value[2:]
        if result.returncode:
            sys.exit(result.returncode)
        output(value)
    native(command, args)
if command == 'df':
    if args != ['-Pk', os.environ['AI_ERP_ROOT']]:
        die()
    output('Filesystem blocks Used Available Capacity Mounted\nf 99999999 1 99999999 1% /')
if command == 'rm':
    if len(args) < 3 or args[:2] != ['-f', '--']:
        die('rm options are outside the deletion contract')
    allowed_shared = {'shared/active-state.json', 'shared/caddy/candidate.Caddyfile',
                      'shared/caddy/upstream.candidate', 'shared/caddy/state.candidate',
                      'shared/caddy/upstream.previous', 'shared/caddy/state.previous',
                      'shared/caddy/upstream.restore', 'shared/caddy/state.restore'}
    for argument in args[2:]:
        path = Path(argument)
        if path.is_symlink() or any(parent.is_symlink() for parent in path.parents):
            die('rm may not follow symbolic links')
        try:
            relative = path.resolve().relative_to(root.resolve()).as_posix()
        except ValueError:
            die('rm target is outside the project host root')
        if path.as_posix() != (root / relative).as_posix():
            die('rm target is not canonical')
        manifest = re.fullmatch(r'releases/[a-f0-9]{40}/(?:manifest\.candidate|manifest\.sha256\.candidate|manifest\.json|manifest\.json\.sha256)', relative)
        backup = re.fullmatch(r'releases/[a-f0-9]{40}/backups/postgres-[0-9]{8}T[0-9]{6}Z-[0-9]+\.(?:dump|json)(?:\.candidate)?', relative)
        if relative not in allowed_shared and not manifest and not backup:
            die('rm target is outside the exact artifact allowlist')
        if manifest or backup:
            reference = os.environ.get('APP_IMAGE', '')
            release = data['images'].get(reference, ['', ''])[1] or reference.removeprefix('ai-erp:')
            if not re.fullmatch('[a-f0-9]{40}', release) or relative.split('/')[1] != release:
                die('rm may only clean the current release artifacts')
        event('remove', path=relative)
    native(command, args)
if command == 'ss':
    if len(args) != 2 or args[0] != '-ltn' or args[1] not in ('sport = :80', 'sport = :443'):
        die()
    output('LISTEN 0 128 *:80' if os.environ.get('FAKE_PORT') == 'host' else 'State Recv-Q Send-Q Local Peer')
if command == 'mv':
    if len(args) != 4 or args[:2] != ['-f', '--']:
        die()
    if args[3].endswith('/active-state.json') and '.restore' not in args[2]:
        fault('state')
    if args[3].endswith('/manifest.json'):
        fault('manifest')
    if args[3].endswith('/manifest.json.sha256'):
        fault('manifest-checksum')
    if args[3].endswith('/active-upstream.caddy') and '.restore' not in args[2]:
        fault('upstream')
    if '/backups/' in args[3] and args[3].endswith('.json'):
        fault('backup-metadata')
    event('file-install:' + Path(args[3]).name)
    native(command, args)
if command == 'ln':
    if len(args) != 3 or args[0] != '--' or not args[2].endswith(('/manifest.json', '/manifest.json.sha256')):
        die()
    fault('manifest' if args[2].endswith('/manifest.json') else 'manifest-checksum')
    event('file-install:' + Path(args[2]).name)
    native(command, args)
if command == 'curl':
    prefix = ['--silent', '--show-error', '--connect-timeout', '3', '--max-time', '10', '--include', '--insecure', '--noproxy', '*']
    if args[:len(prefix)] != prefix:
        die('curl timeout/flags missing')
    tail = args[len(prefix):]
    if len(tail) != 3 or tail[0] != '--resolve':
        die()
    url = urlsplit(tail[2])
    site, path = url.netloc, url.path
    if url.scheme != 'https' or site not in ('192.168.219.100', 'ai-erp.duckdns.org', 'blackcow.duckdns.org') or tail[1] != site + ':443:192.168.219.100' or url.query or url.fragment:
        die('public smoke must use the approved Host/SNI and fixed LAN destination')
    if path not in ('/actuator/health/readiness', '/', '/api/v1/system/configuration', '/api/v1/system/info', '/assets/api-docs/index.html', '/api/v1/me', '/oauth2/authorization/google'):
        die()
    if not data['live']:
        die('no live release', 22)
    if path == '/actuator/health/readiness':
        data['public_count'] = data.get('public_count', 0) + 1
        save()
        if point in ('public', 'recovery') or (point == 'public-second' and data['public_count'] == 2):
            die('public failed', 22)
    event('public:' + path, site=site, resolve=tail[1])
    container = next(value for value in data['containers'].values() if value.get('release') == data['live'] and value['running'])
    body = response_body(path, container)
    release = 'bad' if os.environ.get('FAKE_BAD_HEADER') and path == '/assets/api-docs/index.html' else data['live']
    status = os.environ.get('FAKE_ME_STATUS', '401') if path == '/api/v1/me' else os.environ.get('FAKE_HTTP_STATUS', '200')
    headers = 'X-AI-ERP-Release: ' + release + '\r\n'
    if path == '/oauth2/authorization/google':
        status = '302'
        body = ''
        if site == '192.168.219.100':
            location = 'https://ai-erp.duckdns.org' + path
        else:
            if not container.get('oidc_enabled'):
                die('Google authorization requires enabled app', 1)
            mismatch = os.environ.get('FAKE_OAUTH', '')
            provider = 'foreign.test' if mismatch == 'foreign-provider' else 'accounts.google.com'
            callback = 'wrong.duckdns.org' if mismatch == 'wrong-callback' else site
            query = dict(client_id='do-not-print-google-client-id', response_type='code',
                         redirect_uri='https://' + callback + '/login/oauth2/code/google', state='do-not-print-oidc-state')
            if mismatch == 'missing-state':
                del query['state']
            location = 'https://' + provider + '/o/oauth2/v2/auth?' + urlencode(query)
        headers += 'Location: ' + location + '\r\n'
    output('HTTP/1.1 ' + status + ' Status\r\n' + headers + '\r\n' + body)
if command != 'docker':
    die()
if args == ['info', '--format', '{{.ServerVersion}}']:
    output('29.4.0')
if args[:1] == ['build']:
    if len(args) != 7 or args[1:3] != ['--pull', '--label'] or args[4] != '--tag':
        die()
    release = args[3].removeprefix('org.opencontainers.image.revision=')
    if not re.fullmatch('[a-f0-9]{40}', release) or args[5] != 'ai-erp:' + release or args[6] != os.environ['AI_ERP_PROJECT_DIR']:
        die('build provenance')
    event('build', release=release)
    fault('build')
    ident = 'sha256:' + release + '0' * 24
    data['images']['ai-erp:' + release] = [ident, release]
    data['images'][ident] = [ident, release]
    save()
    sys.exit(0)
if args[:3] == ['image', 'inspect', '--format']:
    if len(args) != 5 or args[3] != '{{.Id}} {{ index .Config.Labels "org.opencontainers.image.revision" }}':
        die()
    if args[4] not in data['images']:
        die('image absent', 1)
    event('image-check')
    output(' '.join(data['images'][args[4]]))
publisher_id = 'c' * 64
impostor_id = 'd' * 64
publisher_modes = ('short-id', 'owned-impostor', 'multiple-owned')
if args[:3] == ['ps', '-q', '--filter'] and len(args) == 4 and args[3] in ('publish=80', 'publish=443'):
    port = os.environ.get('FAKE_PORT', '')
    if port == 'foreign':
        output('foreign')
    if port == 'short-id' and 'caddy' in data['containers']:
        output(publisher_id[:12])
    if port == 'owned-impostor':
        output(impostor_id[:12])
    if port == 'multiple-owned' and 'caddy' in data['containers']:
        output(publisher_id[:12] + '\n' + impostor_id[:12])
    output('caddy' if 'caddy' in data['containers'] and port != 'host' else '')
if args[:1] == ['inspect']:
    if len(args) != 4 or args[1] not in ('-f', '--format'):
        die()
    fmt, container = args[2:]
    labels = '{{ index .Config.Labels "com.docker.compose.project" }} {{ index .Config.Labels "com.docker.compose.service" }}'
    if container in (publisher_id, publisher_id[:12]):
        if fmt == '{{.Id}}':
            output(publisher_id)
        container = 'caddy'
    if container in (impostor_id, impostor_id[:12]):
        if fmt == '{{.Id}}':
            output(impostor_id)
        if fmt == labels:
            output('ai-erp-phase1 caddy')
        die()
    if container == 'foreign' and fmt == labels:
        output('legacy web')
    if container not in data['containers']:
        die('container absent', 1)
    if fmt == '{{.Id}}':
        output(publisher_id if container == 'caddy' else container[0] * 64)
    if fmt == '{{.State.Health.Status}}':
        event('healthy:' + container)
        if point == 'candidate-health' and container.startswith('app-') and data['containers'][container]['release'] == os.environ['FAKE_HEAD']:
            output('unhealthy')
        output(data['containers'][container].get('health', 'healthy') if data['containers'][container]['running'] else 'unhealthy')
    if fmt == '{{.Image}}':
        event('identity:' + container)
        output('sha256:' + 'f' * 64 if point == 'candidate-image' and data['containers'][container]['release'] == os.environ['FAKE_HEAD'] else data['containers'][container]['image'])
    if fmt == labels:
        output('ai-erp-phase1 ' + container)
    die()
if args[:1] in (['volume'], ['network']):
    kind = args[0]
    names = ['ai_erp_phase1_postgres_data', 'ai_erp_phase1_redis_data', 'ai_erp_phase1_caddy_data', 'ai_erp_phase1_caddy_config'] if kind == 'volume' else ['ai-erp-phase1-internal']
    if args[1:] == ['ls', '--format', '{{.Name}}']:
        output('\n'.join(names) if data['resources'] or os.environ.get('FAKE_RESOURCE') else '')
    if len(args) != 5 or args[1:3] != ['inspect', '--format'] or args[4] not in names:
        die()
    expected = '{{ index .Labels "com.docker.compose.project" }} {{ index .Labels "com.docker.compose.' + ('network' if kind == 'network' else 'volume') + '" }}'
    if args[3] != expected:
        die()
    if os.environ.get('FAKE_RESOURCE'):
        output('legacy' if os.environ['FAKE_RESOURCE'] == 'foreign' else '<no value>')
    output('ai-erp-phase1 ' + ('internal' if kind == 'network' else args[4]))
if args == ['compose', 'version', '--short']:
    output('5.1.2')
prefix = ['compose', '--project-name', 'ai-erp-phase1', '--env-file', os.environ['AI_ERP_ROOT'] + '/shared/.env', '-f', os.environ['AI_ERP_PROJECT_DIR'] + '/infra/compose.prod.yml']
if args[:len(prefix)] != prefix:
    die('Compose namespace or arguments')
tail = args[len(prefix):]
if tail == ['config', '--quiet']:
    event('compose-config')
    sys.exit(0)
if tail == ['up', '-d', 'postgres', 'redis', 'caddy']:
    event('infra')
    fault('infra')
    for service in ('postgres', 'redis', 'caddy'):
        data['containers'][service] = dict(running=True, image='infra', release='')
    data['resources'] = {'owned': True}
    save()
    sys.exit(0)
if len(tail) == 4 and tail[:3] == ['up', '-d', '--no-deps'] and tail[3] in ('app-blue', 'app-green'):
    service = tail[3]
    ref = os.environ['APP_IMAGE']
    if ref not in data['images']:
        die('unbuilt candidate', 1)
    ident, release = data['images'][ref]
    if data['live'] and data['containers'].get(service, {}).get('release') == data['live']:
        die('attempt to recreate live slot', 1)
    event('start:' + service, release=release)
    data['containers'][service] = dict(running=True, image=ident, release=release,
                                     oidc_enabled=os.environ['APP_OIDC_ENABLED'] == 'true')
    save()
    sys.exit(0)
if len(tail) == 3 and tail[:2] == ['ps', '-q'] and tail[2] in ('postgres', 'redis', 'caddy', 'app-blue', 'app-green'):
    if tail[2] == 'caddy' and os.environ.get('FAKE_PORT') in publisher_modes and data['containers'].get('caddy', {}).get('running'):
        output(publisher_id)
    output(tail[2] if data['containers'].get(tail[2], {}).get('running') else '')
if len(tail) == 4 and tail[:3] == ['ps', '--all', '-q'] and tail[3] in ('app-blue', 'app-green'):
    output(tail[3] if tail[3] in data['containers'] else '')
if tail == ['--profile', 'migration', 'run', '--rm', 'migrate']:
    event('migrate')
    fault('migrate')
    data['history'] = True
    save()
    sys.exit(0)
if tail == ['run', '--rm', '--no-deps', 'caddy', 'validate', '--config', '/etc/caddy/state/candidate.Caddyfile', '--adapter', 'caddyfile']:
    source = Path(os.environ['AI_ERP_PROJECT_DIR']) / 'infra/Caddyfile'
    event('caddy-validate', caddyChecksum=hashlib.sha256(source.read_bytes()).hexdigest())
    fault('caddy-validate')
    text = (root / 'shared/caddy/candidate.Caddyfile').read_text(encoding='utf-8')
    upstream = (root / 'shared/caddy/upstream.candidate').read_text(encoding='utf-8').rstrip()
    expected = source.read_text(encoding='utf-8').replace('import /etc/caddy/state/active-upstream.caddy', upstream)
    if text.rstrip() != expected.rstrip():
        die('candidate does not match the current checkout Caddyfile', 1)
    sys.exit(0)
if tail == ['exec', '-T', 'caddy', 'caddy', 'reload', '--config', '/etc/caddy/source/Caddyfile', '--adapter', 'caddyfile']:
    source = Path(os.environ['AI_ERP_PROJECT_DIR']) / 'infra/Caddyfile'
    if source.read_text(encoding='utf-8').count('import /etc/caddy/state/active-upstream.caddy') != 1:
        die('runtime config must import the current upstream exactly once', 1)
    text = (root / 'shared/caddy/active-upstream.caddy').read_text()
    match = re.search(r'header X-AI-ERP-Release "([a-f0-9]{40})"', text)
    release = match[1] if match else ''
    restoring = release == data.get('prior_live', '')
    if restoring:
        fault('recovery')
    if not restoring:
        data['prior_live'] = data['live']
        data['public_count'] = 0
        save()
        fault('reload')
    data['live'] = release
    event('restore' if restoring else 'switch', caddyChecksum=hashlib.sha256(source.read_bytes()).hexdigest())
    save()
    sys.exit(0)
if len(tail) == 2 and tail[0] == 'stop' and tail[1] in ('app-blue', 'app-green'):
    event('stop:' + tail[1])
    if tail[1] in data['containers']:
        data['containers'][tail[1]]['running'] = False
    save()
    sys.exit(0)
if tail[:3] == ['exec', '-T', 'postgres']:
    if not data['containers'].get('postgres', {}).get('running'):
        die('database command without running PostgreSQL', 1)
    operation = tail[3:]
    if operation == ['psql', '-U', 'ai_erp', '-d', 'ai_erp', '-Atqc', "select to_regclass('platform.flyway_schema_history') is not null"]:
        event('probe')
        fault('probe')
        output('t' if data['history'] else 'f')
    if operation == ['pg_dump', '-U', 'ai_erp', '-d', 'ai_erp', '-Fc']:
        event('backup')
        fault('backup')
        data['backed_up'] = True
        save()
        output('PGDMP-valid-test-backup')
    if operation == ['pg_restore', '-l']:
        event('backup-validate')
        fault('backup-validate')
        if not sys.stdin.read().startswith('PGDMP'):
            die('invalid dump', 1)
        sys.exit(0)
    die()
if tail[:2] == ['exec', '-T'] and len(tail) == 12 and tail[2] in ('app-blue', 'app-green'):
    if tail[3:11] != ['curl', '--silent', '--show-error', '--connect-timeout', '3', '--max-time', '10', '--include']:
        die()
    path = tail[11].removeprefix('http://127.0.0.1:8080')
    if path not in ('/actuator/health/readiness', '/', '/api/v1/system/configuration', '/api/v1/system/info', '/assets/api-docs/index.html', '/api/v1/me'):
        die()
    fault('internal')
    if not data['containers'].get(tail[2], {}).get('running'):
        die('internal smoke on absent app')
    event('internal:' + path)
    status = os.environ.get('FAKE_ME_STATUS', '401') if path == '/api/v1/me' else os.environ.get('FAKE_HTTP_STATUS', '200')
    output('HTTP/1.1 ' + status + ' Status\r\n\r\n' + response_body(path, data['containers'][tail[2]]))
die()
