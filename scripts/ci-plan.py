"""Conservative test impact graph; the root architectural/contract gate is unconditional."""
import argparse, json, re, subprocess
APPS = ['user','content','quiz','event-receiver','ai-platform','notificator','payment','admin']
INFRA = ['gateway','config-server','discovery-server']
LIBS = ['integration','observability']
ALL = ['root'] + APPS + INFRA + LIBS
DEPENDENTS = {'user': ['admin','notificator','payment','quiz'], 'content': ['admin','quiz'],
              'payment': ['admin','content'], 'notificator': ['admin','quiz'],
              'ai-platform': ['content','quiz'], 'event-receiver': ['notificator']}
def plan(paths):
    selected = {'root'}
    for path in paths:
        if path.startswith(('docs/','tasks/')) or path in ('README.md','AGENTS.md'):
            continue
        match = re.match(r'(services|infrastructure)/erudit-([^/]+)/', path)
        if match and match[2] in ALL:
            selected.update((match[2], 'gateway'))
        else:
            return ALL  # shared libraries, build/config/CI/scripts and unknown paths affect everything
    pending = list(selected)
    while pending:
        for dependent in DEPENDENTS.get(pending.pop(), []):
            if dependent not in selected:
                selected.add(dependent); pending.append(dependent)
    return [name for name in ALL if name in selected]
def matrix(names):
    def module(name):
        if name == 'root': return ':'
        group = 'services' if name in APPS else 'infrastructure' if name in INFRA else 'platform'
        return ':' + group + ':erudit-' + name + ':'
    return {'include': [dict(id=name,module=module(name)) for name in names]}
if __name__ == '__main__':
    args=argparse.ArgumentParser();args.add_argument('--base',default='');args.add_argument('--head',default='HEAD');v=args.parse_args()
    if not re.fullmatch(r'[0-9a-fA-F]{40}',v.base): names=ALL
    else:
        result=subprocess.run(['git','diff','--name-only','-z',v.base,v.head],capture_output=True)
        names=ALL if result.returncode else plan([p for p in result.stdout.decode('utf-8').split('\0') if p])
    print(json.dumps(matrix(names),separators=(',',':')))
