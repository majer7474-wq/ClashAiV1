"""Stage FirstLight's five verified Android policies from pinned source checkouts."""
import hashlib
import json
import shutil
import struct
import subprocess
import sys
from pathlib import Path

FL_REV = "cf16ef1759f465d74f1797c5e92fe3d939bbb4fc"
FIRST_REV = "4c82496ad5af49a3c7ea2392f26ff7bac33d9707"


def verify_sources(fl, first):
    for path, revision in ((fl, FL_REV), (first, FIRST_REV)):
        head = subprocess.check_output(["git", "-C", path, "rev-parse", "HEAD"], text=True)
        if head.strip() != revision:
            raise ValueError(f"Unexpected source revision in {path}")
    for entry in json.loads((first / "checkpoints/manifest.json").read_text())["models"]:
        path = first / "checkpoints" / entry["path"]
        with path.open("rb") as stream:
            if hashlib.file_digest(stream, "sha256").hexdigest() != entry["sha256"]:
                raise ValueError(f"Checkpoint checksum mismatch: {path}")


def stage_reference_case(fl, assets, catalog):
    import numpy as np
    import onnxruntime as ort
    import torch

    sys.path.insert(0, str(fl / "research/mobile_inference"))
    from probe_export import flatten
    from schema import representative_batch

    tensors = []
    flatten(representative_batch(), tensors)
    tensors += [torch.zeros(1, 512), torch.zeros(1, 512), torch.ones(1, dtype=torch.uint8)]
    arrays = [t.detach().cpu().numpy() for t in tensors]
    arrays = [a.astype(np.uint8) if a.dtype == np.bool_ else a for a in arrays]
    schema = json.loads((assets / "abi/production_tensor_schema_uint8.json").read_text())
    with (assets / "reference.bin").open("wb") as stream:
        for a, spec in zip(arrays, schema["inputs"], strict=True):
            if a.nbytes != spec["bytes"] or list(a.shape) != spec["shape"] or a.dtype.name != spec["dtype"]:
                raise ValueError(f"Reference tensor mismatch at {spec['name']}")
            stream.write(struct.pack(">I", a.nbytes))
            stream.write(a.astype(a.dtype.newbyteorder("<"), copy=False).tobytes())
    expected = {}
    options = ort.SessionOptions()
    options.intra_op_num_threads = 2
    options.log_severity_level = 3
    for entry in catalog["models"]:
        session = ort.InferenceSession(str(fl / entry["source"]), options, providers=["CPUExecutionProvider"])
        feeds = {node.name: a for node, a in zip(session.get_inputs(), arrays, strict=True)}
        expected[entry["id"]] = [a.reshape(-1).tolist() for a in session.run(None, feeds)[:6]]
        del session
    (assets / "reference.json").write_text(json.dumps(expected))


def prepare(fl, first):
    verify_sources(fl, first)
    (fl / "build.local.json").write_text(json.dumps({
        "firstlight_cr": str(first), "java_home": sys.prefix, "runtime_update": "."
    }))
    subprocess.run([sys.executable, fl / "tools/export_models.py", "--force"], check=True)
    output = Path(__file__).resolve().parent / "app/build/firstlight"
    assets = output / "assets/firstlight"
    java = output / "java/com/firstlight/offline"
    for folder in (assets / "abi", assets / "models", java):
        folder.mkdir(parents=True, exist_ok=True)
    catalog = json.loads((fl / "build/android_model_catalog.json").read_text())
    for entry in catalog["models"]:
        shutil.copyfile(fl / entry["source"], output / "assets" / entry["asset"])
    for source in ("research/mobile_inference/production_tensor_schema_uint8.json",
                   "research/runtime_fallback/binding_fullshape_uint8.json"):
        shutil.copy(fl / source, assets / "abi")
    stage_reference_case(fl, assets, catalog)
    for name in ("OnDeviceOrtPolicy.java", "RecurrentPolicySession.java"):
        shutil.copy(fl / "on_device/java/com/firstlight/offline" / name, java)
    shutil.copy(fl / "build/generated-java/com/firstlight/offline/PolicyModelCatalog.java", java)
    for origin, prefix in ((fl, "FL-Royale"), (first, "FirstLight-CR")):
        for name in ("LICENSE", "NOTICE"):
            shutil.copyfile(origin / name, assets / f"{prefix}-{name}.txt")


if __name__ == "__main__":
    prepare(*(Path(arg).resolve() for arg in sys.argv[1:]))
