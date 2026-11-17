from pathlib import Path
import json
import onnx

model_path = Path("best.pt")
output_path = Path("best.onnx")

if not model_path.exists():
    raise SystemExit("GREŠKA: best.pt nije pronađen")

from ultralytics import YOLO

model = YOLO(str(model_path))
model.export(
    format="onnx",
    imgsz=800,
    opset=12,
    simplify=False,
    dynamic=False,
    half=False
)

if not output_path.exists():
    raise SystemExit("GREŠKA: ONNX datoteka nije nastala")

graph = onnx.load(str(output_path))
onnx.checker.check_model(graph)

names = model.names
if isinstance(names, list):
    names = {i: name for i, name in enumerate(names)}
Path("classes.json").write_text(
    json.dumps({str(k): str(v) for k, v in names.items()}, ensure_ascii=False),
    encoding="utf-8"
)

print("\n=== ONNX INPUTS ===")
for item in graph.graph.input:
    dims = [dim.dim_value if dim.dim_value else dim.dim_param
            for dim in item.type.tensor_type.shape.dim]
    print(item.name, dims)

print("\n=== ONNX OUTPUTS ===")
for item in graph.graph.output:
    dims = [dim.dim_value if dim.dim_value else dim.dim_param
            for dim in item.type.tensor_type.shape.dim]
    print(item.name, dims)

print("Classes:", len(names))
print("ONNX size:", output_path.stat().st_size, "bytes")
print("ONNX validation: OK")
