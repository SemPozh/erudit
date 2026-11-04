"""Render the acceptance API inventory from the service-owned OpenAPI files."""
import argparse
from pathlib import Path
import yaml

ROOT = Path(__file__).resolve().parent.parent
TARGET = ROOT / 'docs/api/endpoints.md'
METHODS = {'get', 'post', 'put', 'patch', 'delete', 'head', 'options', 'trace'}

def render():
    public, internal = [], []
    for spec in sorted((ROOT / 'services').glob('*/src/main/resources/static/openapi.yaml')):
        owner = spec.relative_to(ROOT).parts[1]
        for path, item in yaml.safe_load(spec.read_text(encoding='utf-8'))['paths'].items():
            for method, operation in item.items():
                if method not in METHODS:
                    continue
                row = f'| {owner} | {method.upper()} | `{path}` | `{operation["operationId"]}` |'
                (public if path.startswith('/api/v1/') else internal).append(row)
    lines = ['# Каталог HTTP API ЛР2', '',
             'Источник: service-owned `openapi.yaml`; обновить: `python scripts/api-inventory.py`.',
             'Проверка актуальности: `python scripts/api-inventory.py --check` (PyYAML).', '',
             '## Публичные операции через Gateway', '', f'Всего: {len(public)}. UUID, query, тела, статусы и примеры — в Gateway Swagger.', '',
             '| Владелец | Метод | Путь | operationId |', '| --- | --- | --- | --- |', *public, '',
             '## Внутренние операции', '', f'Всего: {len(internal)}. Только частная сеть и Feign; Gateway их не публикует.', '',
             '| Владелец | Метод | Путь | operationId |', '| --- | --- | --- | --- |', *internal, '',
             '## Платформа и диагностика', '',
             '| Владелец | Метод/путь | Доступ |', '| --- | --- | --- |',
             '| Gateway | GET `/swagger-ui/index.html`, `/swagger-ui/**` | public |',
             '| Gateway | GET `/openapi/swagger-config`, `/openapi/{service-name}` | public; 8 business specs |',
             '| Все приложения | GET `/actuator/health`, `/actuator/health/liveness`, `/actuator/health/readiness`, `/actuator/info`, `/actuator/prometheus` | извне только Gateway |',
             '| Config Server | GET `/{application}/{profile}` | частная сеть; native repository |',
             '| Eureka | GET `/`, `/eureka/apps`, `/eureka/apps/{app}`; регистрация/heartbeat через Eureka client | частная сеть |', '',
             'Отладочные порты opt-in/loopback: `docker-compose.debug.yml`. Native Config Server',
             'также предоставляет стандартные форматы Spring Cloud Config; это служебный API,',
             'не часть бизнес-контракта Gateway. HEAD/OPTIONS следуют HTTP/CORS семантике.', '']
    return '\n'.join(lines)

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    content = render()
    if args.check:
        if not TARGET.exists() or TARGET.read_text(encoding='utf-8') != content:
            raise SystemExit('API inventory is stale: run python scripts/api-inventory.py')
    else:
        TARGET.write_text(content, encoding='utf-8')
