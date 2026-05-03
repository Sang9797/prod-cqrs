#!/usr/bin/env python3
"""
Generates a Postman collection by dynamically parsing:
  src/main/resources/graphql/*.graphqls  -> GraphQL queries + mutations
  src/main/java/**/*Controller.java      -> REST endpoints (@RestController only)

Adding a new @RestController or GraphQL operation is picked up automatically.
"""
import glob, json, os, re

BASE_URL = os.environ.get("BASE_URL", "http://localhost:8080")
_SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
_PROJECT_DIR = os.path.dirname(_SCRIPT_DIR)


def _glob(pattern):
    return sorted(glob.glob(os.path.join(_PROJECT_DIR, pattern), recursive=True))


# ── GraphQL schema ─────────────────────────────────────────────────────────────

def _parse_graphql():
    files = _glob("src/main/resources/graphql/**/*.graphqls")
    if not files:
        return [], [], {}
    all_text = "\n".join(open(f).read() for f in files)

    # named_types: {TypeName: [{name, base_type}, ...]}
    named_types = {}
    for m in re.finditer(r"(?:type|input)\s+(\w+)\s*\{([^}]+)\}", all_text, re.DOTALL):
        tname, body = m.group(1), m.group(2)
        if tname in ("Query", "Mutation", "Subscription"):
            continue
        fields = []
        for line in body.splitlines():
            line = re.sub(r"#.*", "", line).strip()
            fm = re.match(r"(\w+)\s*(?:\([^)]*\))?\s*:\s*([\[\]!\w]+)", line)
            if fm:
                fields.append({"name": fm.group(1),
                                "base_type": re.sub(r"[\[\]!]", "", fm.group(2))})
        named_types[tname] = fields

    def _ops(block):
        block = re.sub(r"#[^\n]*", "", block)
        ops = []
        for m in re.finditer(r"(\w+)\s*(\([^)]*\))?\s*:\s*([\[\]!\w]+)", block, re.DOTALL):
            name = m.group(1)
            params_raw = (m.group(2) or "").strip("()")
            ret_type = re.sub(r"[\[\]!]", "", m.group(3))
            params = []
            for part in re.split(r"[\n,]", params_raw):
                part = part.strip()
                pm = re.match(r"(\w+)\s*:\s*([\[\]!\w]+)(?:\s*=\s*(\S+))?", part)
                if pm:
                    params.append({"name": pm.group(1),
                                   "gql_type": pm.group(2),
                                   "base_type": re.sub(r"[\[\]!]", "", pm.group(2)),
                                   "default": pm.group(3)})
            ops.append({"name": name, "params": params, "return_type": ret_type,
                        "return_fields": [f["name"] for f in named_types.get(ret_type, [])]})
        return ops

    queries, mutations = [], []
    qm = re.search(r"type\s+Query\s*\{([^}]+)\}", all_text, re.DOTALL)
    if qm:
        queries = _ops(qm.group(1))
    mm = re.search(r"type\s+Mutation\s*\{([^}]+)\}", all_text, re.DOTALL)
    if mm:
        mutations = _ops(mm.group(1))
    return queries, mutations, named_types


_SCALARS = {"Int": 0, "Float": 0.0, "Boolean": False, "String": "", "ID": ""}


def _example(base_type, named_types, default=None):
    """Return an example value for a GraphQL scalar or input type."""
    if default and default != "null":
        for conv in (int, float):
            try:
                return conv(default)
            except (ValueError, TypeError):
                pass
        return {"true": True, "false": False}.get(default.lower(), default)
    if base_type in _SCALARS:
        return _SCALARS[base_type]
    # Input type — build a typed nested object
    if base_type in named_types:
        return {f["name"]: _SCALARS.get(f["base_type"], "") for f in named_types[base_type]}
    return None


def _gql_item(op, is_mutation, named_types):
    kw = "mutation" if is_mutation else "query"
    name, params, ret_fields = op["name"], op["params"], op["return_fields"]

    var_decls = [f'${p["name"]}: {p["gql_type"]}' for p in params]
    var_args = [f'{p["name"]}: ${p["name"]}' for p in params]
    variables = {p["name"]: _example(p["base_type"], named_types, p["default"]) for p in params}

    decl = f"({', '.join(var_decls)})" if var_decls else ""
    args = f"({', '.join(var_args)})" if var_args else ""
    fields_block = (" {\n    " + "\n    ".join(ret_fields) + "\n  }") if ret_fields else ""
    query_str = f"{kw}{decl} {{\n  {name}{args}{fields_block}\n}}"
    display = re.sub(r"([A-Z])", r" \1", name).strip().title()

    return {
        "name": display,
        "request": {
            "method": "POST",
            "header": [{"key": "Content-Type", "value": "application/json"}],
            "url": {"raw": "{{baseUrl}}/graphql",
                    "host": ["{{baseUrl}}"], "path": ["graphql"]},
            "body": {"mode": "graphql", "graphql": {
                "query": query_str,
                "variables": json.dumps(variables, indent=2),
            }},
        },
    }


# ── Java REST controllers ──────────────────────────────────────────────────────

_HTTP_MAP = {"GetMapping": "GET", "PostMapping": "POST",
             "PutMapping": "PUT", "DeleteMapping": "DELETE", "PatchMapping": "PATCH"}


def _parse_controllers():
    controllers = []
    for path in _glob("src/main/java/**/*Controller.java"):
        content = open(path).read()
        if "@RestController" not in content:
            continue
        class_m = re.search(r"public class (\w+)", content)
        if not class_m:
            continue
        folder = class_m.group(1).removesuffix("Controller") or class_m.group(1)

        base_m = re.search(r'@RequestMapping\(\s*"([^"]+)"\s*\)', content)
        base = base_m.group(1) if base_m else ""

        endpoints = []
        ann_pat = re.compile(
            r'@(GetMapping|PostMapping|PutMapping|DeleteMapping|PatchMapping)'
            r'(?:\s*\(\s*"([^"]*)"\s*\))?'
        )
        for am in ann_pat.finditer(content):
            http_method = _HTTP_MAP[am.group(1)]
            full_path = base + (am.group(2) or "")

            region = content[am.end(): am.end() + 2000]
            pub_pos = re.search(r"\bpublic\b", region)
            if not pub_pos:
                continue

            # @Operation summary lives between the mapping and 'public'
            before_pub = region[:pub_pos.start()]
            sum_m = re.search(r'summary\s*=\s*"([^"]+)"', before_pub)
            summary = sum_m.group(1) if sum_m else None

            # Method name is the first word followed by '(' after 'public'
            after_pub = region[pub_pos.end():]
            name_m = re.search(r"(\w+)\s*\(", after_pub[:500])
            if not name_m:
                continue
            method_name = name_m.group(1)
            display = summary or re.sub(r"([A-Z])", r" \1", method_name).strip().title()

            # Find balanced method params (handles nested parens from annotations)
            paren_open = am.end() + pub_pos.end() + name_m.end()
            depth, i = 1, paren_open
            while i < len(content) and depth > 0:
                c = content[i]
                if c == "(":
                    depth += 1
                elif c == ")":
                    depth -= 1
                i += 1
            params_text = content[paren_open: i - 1]

            has_body = "@RequestBody" in params_text

            # Auto-detect @RequestParam → query params
            query_params = []
            rp_pat = re.compile(
                r"@RequestParam\s*(?:\(([^)]*)\))?\s*"   # @RequestParam(options)?
                r"(?:@\w+\s*(?:\([^)]*\))?\s*)*"         # skip validation annotations
                r"\w+\s+(\w+)"                            # type + name
            )
            for rp in rp_pat.finditer(params_text):
                opts = rp.group(1) or ""
                pname = rp.group(2)
                dv_m = re.search(r'defaultValue\s*=\s*"([^"]*)"', opts)
                query_params.append({"name": pname,
                                     "default": dv_m.group(1) if dv_m else ""})

            endpoints.append({"method": http_method, "path": full_path, "name": display,
                               "has_body": has_body, "query_params": query_params})

        if endpoints:
            controllers.append({"folder": folder, "endpoints": endpoints})
    return controllers


# ── Known example request bodies ───────────────────────────────────────────────

_BODIES = {
    "/api/v1/auth/login":
        '{\n  "username": "{{username}}",\n  "password": "{{password}}"\n}',
    "/api/v1/orders":
        ('{\n  "customerId": "customer-001",\n  "items": [\n'
         '    {\n      "productId": "prod-001",\n      "productName": "Widget",\n'
         '      "quantity": 2,\n      "unitPrice": 29.99,\n      "currency": "USD"\n'
         '    }\n  ]\n}'),
    "/api/v1/orders/{orderId}":
        '{\n  "reason": "Customer requested cancellation"\n}',
    "/api/v1/inventory/reserve":
        '{\n  "productId": "prod-001",\n  "warehouseId": "wh-001",\n  "quantity": 5,\n  "orderId": "{{orderId}}"\n}',
    "/api/v1/inventory/release":
        '{\n  "productId": "prod-001",\n  "warehouseId": "wh-001",\n  "quantity": 5,\n  "orderId": "{{orderId}}"\n}',
    "/api/v1/inventory/adjust":
        '{\n  "productId": "prod-001",\n  "warehouseId": "wh-001",\n  "delta": 50,\n  "reason": "Received shipment"\n}',
}


def _rest_item(ep):
    path, method, name = ep["path"], ep["method"], ep["name"]
    postman_path = re.sub(r"\{(\w+)\}", r"{{\1}}", path)
    url = {
        "raw": f"{{{{baseUrl}}}}{postman_path}",
        "host": ["{{baseUrl}}"],
        "path": [s for s in postman_path.lstrip("/").split("/") if s],
    }
    if ep["query_params"]:
        qs = "&".join(f'{p["name"]}={p["default"]}' for p in ep["query_params"])
        url["raw"] = f"{{{{baseUrl}}}}{postman_path}?{qs}"
        url["query"] = [{"key": p["name"], "value": p["default"]} for p in ep["query_params"]]

    item = {"name": name, "request": {"method": method, "header": [], "url": url}}

    if ep["has_body"]:
        item["request"]["header"].append({"key": "Content-Type", "value": "application/json"})
        item["request"]["body"] = {
            "mode": "raw",
            "raw": _BODIES.get(path, "{\n  \n}"),
            "options": {"raw": {"language": "json"}},
        }

    # Special post-response scripts
    if path == "/api/v1/auth/login" and method == "POST":
        item["request"]["auth"] = {"type": "noauth"}
        item["event"] = [{"listen": "test", "script": {"type": "text/javascript", "exec": [
            "if (pm.response.code === 200) {",
            "  pm.collectionVariables.set('token', pm.response.json().token);",
            "  pm.test('Token saved', () => pm.expect(pm.response.json().token).to.be.a('string'));",
            "}",
        ]}}]
    elif path == "/api/v1/orders" and method == "POST":
        item["event"] = [{"listen": "test", "script": {"type": "text/javascript", "exec": [
            "if (pm.response.code === 201) {",
            "  pm.collectionVariables.set('orderId', pm.response.json().id);",
            "  pm.test('Order ID saved', () => pm.expect(pm.response.json().id).to.be.a('string'));",
            "}",
        ]}}]

    return item


# ── Main ───────────────────────────────────────────────────────────────────────

_FOLDER_ORDER = {"Auth": 0, "Order": 1, "Inventory": 2}


def main():
    out_dir = os.path.join(_PROJECT_DIR, "postman")
    out_file = os.path.join(out_dir, "collection.json")
    os.makedirs(out_dir, exist_ok=True)

    queries, mutations, named_types = _parse_graphql()
    controllers = _parse_controllers()

    items = []
    for ctrl in sorted(controllers, key=lambda c: (_FOLDER_ORDER.get(c["folder"], 99), c["folder"])):
        items.append({"name": ctrl["folder"],
                      "item": [_rest_item(ep) for ep in ctrl["endpoints"]]})

    if queries:
        items.append({"name": "GraphQL Queries",
                      "item": [_gql_item(op, False, named_types) for op in queries]})
    if mutations:
        items.append({"name": "GraphQL Mutations",
                      "item": [_gql_item(op, True, named_types) for op in mutations]})

    collection = {
        "info": {
            "name": "CQRS Order Service",
            "description": "Production-grade CQRS Order Service — Spring Boot 4 / Java 25 / Virtual Threads",
            "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json",
        },
        "variable": [
            {"key": "baseUrl",  "value": BASE_URL,    "type": "string"},
            {"key": "username", "value": "admin",      "type": "string"},
            {"key": "password", "value": "admin123",    "type": "string"},
            {"key": "token",    "value": "",            "type": "string"},
            {"key": "orderId",  "value": "",            "type": "string"},
        ],
        "auth": {
            "type": "bearer",
            "bearer": [{"key": "token", "value": "{{token}}", "type": "string"}],
        },
        "event": [{"listen": "prerequest", "script": {"type": "text/javascript", "exec": [
            "// Auto-login: runs before every request.",
            "// pm.sendRequest callback fires before the main request is sent,",
            "// but the collection bearer {{token}} is already resolved — so we",
            "// also call pm.request.headers.upsert to inject the header directly.",
            "const token = pm.collectionVariables.get('token');",
            "if (!token) {",
            "  pm.sendRequest({",
            "    url: pm.collectionVariables.get('baseUrl') + '/api/v1/auth/login',",
            "    method: 'POST',",
            "    header: { 'Content-Type': 'application/json' },",
            "    body: {",
            "      mode: 'raw',",
            "      raw: JSON.stringify({",
            "        username: pm.collectionVariables.get('username'),",
            "        password: pm.collectionVariables.get('password')",
            "      })",
            "    }",
            "  }, function (err, res) {",
            "    if (err || res.code !== 200) {",
            "      console.error('Auto-login failed:', err || ('HTTP ' + res.code));",
            "      return;",
            "    }",
            "    const newToken = res.json().token;",
            "    pm.collectionVariables.set('token', newToken);",
            "    // Inject directly so THIS request also gets the token",
            "    pm.request.headers.upsert({ key: 'Authorization', value: 'Bearer ' + newToken });",
            "  });",
            "}",
        ]}}],
        "item": items,
    }

    with open(out_file, "w") as fh:
        json.dump(collection, fh, indent=2)

    print(f"Postman collection generated: {out_file}")
    print()
    print(f"  REST folders:      {', '.join(c['folder'] for c in controllers)}")
    print(f"  GraphQL queries:   {', '.join(q['name'] for q in queries) or 'none'}")
    print(f"  GraphQL mutations: {', '.join(m['name'] for m in mutations) or 'none'}")
    print()
    print("Import: Postman → Import → Upload File → postman/collection.json")
    if BASE_URL != "http://localhost:8080":
        print(f"Base URL: {BASE_URL}")


if __name__ == "__main__":
    main()
