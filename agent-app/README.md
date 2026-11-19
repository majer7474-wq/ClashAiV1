# Clash Vision Agent for Android

The foreground service captures frames with MediaProjection. The model test runs a selected FirstLight V4 policy on a fixed battle observation and checks six action outputs against desktop ONNX Runtime. Elapsed time measures inference only. Screen capture and the model test are independent. Live perception, action dispatch and match learning are unimplemented.

## Build

The build-agent workflow checks out pinned FL Royale and FirstLight CR sources, verifies the five checkpoint hashes, exports each policy to ONNX and checks its outputs against PyTorch. It stages the model catalog, tensor schema, bindings, recurrent policy runtime and license notices under app/build/firstlight before invoking Gradle. If building locally, use the Python dependencies and preparation command in that workflow before running Gradle. Run preparation again after a Gradle clean.

The model test is available under “Testiraj model na spremljenom stanju”. A successful result verifies model execution on the reference state. It does not place a card. The service can capture independently using Android's consent dialog.
