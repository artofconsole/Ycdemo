name: Unzip Archive

on:
  workflow_dispatch:

jobs:
  unzip:
    runs-on: ubuntu-latest
    permissions:
      contents: write
    steps:
      - name: Checkout repository
        uses: actions/checkout@v4

      - name: Extract zip to root
        run: unzip -o ycloud.zip -d .

      - name: Commit extracted files
        run: |
          git config user.name "github-actions[bot]"
          git config user.email "41898282+github-actions[bot]@users.noreply.github.com"
          git add -A
          git reset -- ycloud.zip .github
          git commit -m "Extract ycloud.zip to root" || exit 0
          git push
