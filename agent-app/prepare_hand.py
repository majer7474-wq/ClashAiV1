"""Convert the pinned Commander hand classifier for the agent's existing ONNX runtime."""
import hashlib
import json
from pathlib import Path
import shutil
import sys

import numpy as np
import onnxruntime as ort
from PIL import Image
import torch


def prepare(source, battle):
    assets = source / "app/src/main/assets"
    target = Path(__file__).resolve().parent / "app/build/firstlight/assets/vision"
    target.mkdir(parents=True, exist_ok=True)
    for name, digest in {
        "card_classifier.ptl": "d21b239231d377deecca050c2108d2db57ece40f4e5e336da73acfe0ea85dd8b",
        "card_classes.json": "b39cbe8bf3294132b65201418385da35cf776419014dfaac9b13acfadb55409a",
    }.items():
        if hashlib.sha256((assets / name).read_bytes()).hexdigest() != digest:
            raise ValueError(f"Unexpected hand model asset: {name}")
    model = torch.jit.load(str(assets / "card_classifier.ptl"), map_location="cpu").eval()
    output = target / "hand.onnx"
    torch.onnx.export(model, (torch.zeros(1, 3, 224, 224),), str(output), opset_version=17,
                      dynamo=False, input_names=["rgb"], output_names=["probs"])
    labels = json.loads((assets / "card_classes.json").read_text())
    options = ort.SessionOptions()
    options.intra_op_num_threads = 2
    session = ort.InferenceSession(str(output), options, providers=["CPUExecutionProvider"])
    image = Image.open(battle).convert("RGB")
    w, h = image.size
    expected = ["mega-knight", "skeleton-dragons", "rocket", "arrows", "cannon-cart"]
    for slot, center in enumerate([.31, .499, .687, .875, .092]):
        width = (.078 if slot == 4 else .164) * w
        bottom = (.985 if slot == 4 else .943) * h
        bottom = int(bottom)
        box = tuple(map(int, [center * w - width / 2, bottom - width * 1.23,
                              center * w + width / 2, bottom]))
        crop = image.crop(box).resize((224, 224), Image.Resampling.BILINEAR)
        pixels = np.asarray(crop).astype(np.float32) / 255
        pixels = np.clip(pixels * (.45 / pixels.mean()), 0, 1)
        rgb = pixels.transpose(2, 0, 1)[None].copy()
        probabilities = session.run(None, {"rgb": rgb})[0]
        np.testing.assert_allclose(probabilities, model(torch.from_numpy(rgb)).detach().numpy(),
                                   atol=1e-5, rtol=1e-4)
        if labels[str(probabilities.argmax())] != expected[slot] or probabilities.max() < .55:
            raise ValueError(f"Incorrect reference classification for slot {slot}")
    shutil.copy(assets / "card_classes.json", target)
    shutil.copyfile(source / "LICENSE", target / "Commander-LICENSE.txt")


if __name__ == "__main__":
    prepare(*(Path(arg).resolve() for arg in sys.argv[1:]))
