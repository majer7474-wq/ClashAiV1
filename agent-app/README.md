# Clash Vision Agent for Android

The foreground service captures frames with MediaProjection. The model test runs a selected FirstLight V4 policy on a fixed battle observation and checks six action outputs against desktop ONNX Runtime. Elapsed time measures inference only. Screen capture and the model test are independent. On a single-game portrait capture the service estimates filled elixir segments and reports unknown if the gauge is ambiguous. After detecting the gauge, the service runs the Commander classifier on the four hand slots and the next card. For uncertain predictions on supported tablet layouts it also compares the portrait with nine greyscale templates from clash-jev (MIT). The build verifies all five predictions on the FL Royale battle screenshot. Arena recognition, live policy input, action dispatch and match learning are unimplemented.

## Build

The build-agent workflow checks out pinned FL Royale and FirstLight CR sources, verifies the five checkpoint hashes, exports each policy to ONNX and checks its outputs against PyTorch. It stages the model catalog, tensor schema, bindings, recurrent policy runtime and license notices under app/build/firstlight before invoking Gradle. If building locally, use the Python dependencies and preparation commands in that workflow before running Gradle. Run preparation again after a Gradle clean.

The model test is available under “Testiraj model na spremljenom stanju”. A successful result verifies model execution on the reference state. It does not place a card. The service can capture independently using Android's consent dialog.

The reference templates in app/src/main/assets/vision/hand-shapes.txt are the shape vectors from bytelabs-oss/clash-jev at c1f4893c1627d593938d53910c2568d1addcb8d2. The bundled MIT notice identifies their author. The live HUD reader has been checked against that project's battle_419x633, tablet_hand_lit and tablet_hand_grey frames. These are recordings, not a live device test.
