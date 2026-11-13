from pathlib import Path
import onnx

model_path = Path("best.pt")
output_path = Path("best.onnx")

if not model_path.exists():
    raise SystemExit("GREŠKA: best.pt nije pronađen")

print("YOLO model:", model_path.stat().st_size, "bytes")

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

print("\n=== ONNX INPUTS ===")
for item in graph.graph.input:
    dims = []
    for dim in item.type.tensor_type.shape.dim:
        dims.append(dim.dim_value if dim.dim_value else dim.dim_param)
    print(item.name, dims)

print("\n=== ONNX OUTPUTS ===")
for item in graph.graph.output:
    dims = []
    for dim in item.type.tensor_type.shape.dim:
        dims.append(dim.dim_value if dim.dim_value else dim.dim_param)
    print(item.name, dims)

print("\n=== MODEL METADATA ===")
print("Ultralytics names:", model.names)
print("ONNX size:", output_path.stat().st_size, "bytes")
print("ONNX validation: OK")
