# Scanner Vision Progress

Updated: 2026-05-19

## Completed

- Rebuilt the scanner vision flow on `feature/scanner-vision-upgrade` after merging notification work.
- Improved scanner output quality for indoor scene photos:
  - increased YOLO proposal count from 5 to 10;
  - increased maximum returned labels from 3 to 6;
  - normalized overly specific `piano stool` output to `stool`;
  - kept only the strongest taxonomy interpretation per crop to reduce duplicate labels;
  - added geometry filtering for tiny or extreme-aspect YOLO boxes;
  - added a final minimum confidence threshold via `MIN_OUTPUT_CONF`.
- Verified the coffee shop image at `D:\test_images\modern-coffee-shop-interior-design-with-unique-wooden-ceiling-and-large-windows-photo.jpeg` directly against `http://localhost:8000/analyze`.

## Latest Verification Result

The same coffee shop image now returns:

- `bar stool` at `0.060751`
- `plant pot` at `0.045115`
- `dining chair` at `0.033304`
- `table` at `0.026081`

The previous weak `curtain` result was removed by the confidence threshold, and duplicate/confusing stool variants are no longer returned from the same crop.

## Remaining Work

- Test a small scanner image set, not only one coffee shop image, to tune `MIN_OUTPUT_CONF`, `MIN_BOX_AREA`, and `MAX_BOX_ASPECT`.
- Improve taxonomy coverage for common indoor/cafe objects such as counter, coffee machine, ceiling light, window, sofa, bench, and menu board.
- Add a debug mode or diagnostic endpoint that reports YOLO proposal count, filtered box count, top taxonomy candidates, and rejection reasons.
- Consider using YOLO class labels as soft hints instead of ignoring them completely.
- Add automated regression tests for duplicate suppression and confidence filtering once a stable image fixture strategy is chosen.
