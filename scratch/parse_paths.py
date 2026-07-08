import json

filepath = "/home/samuelsean/.gemini/antigravity/brain/4df28f45-ca14-43ea-a87b-e57574f0526b/.system_generated/steps/13948/content.md"

with open(filepath, "r", encoding="utf-8") as f:
    lines = f.readlines()

json_str = ""
for line in lines:
    if line.strip().startswith("{"):
        json_str = line.strip()
        break

data = json.loads(json_str)
schemas = data.get("components", {}).get("schemas", {})

target_schemas = [
    "UserOrganizationAccessResponse"
]

for ts in target_schemas:
    if ts in schemas:
        print(f"========================================\nSCHEMA: {ts}\n========================================")
        print(json.dumps(schemas[ts], indent=2))
    else:
        print(f"========================================\nSCHEMA NOT FOUND: {ts}\n========================================")
