# Material Symbols vector drawables

The drawables in `drawable/` are copies of Google's Material Symbols icons.
They're bundled here rather than coming from
`androidx.compose.material:material-icons-core`: that library is the frozen
legacy Material Icons set, and `androidx.compose.material3:material3` stopped
pulling it in transitively as of 1.4.0.

This directory is wired in as an extra resource dir in `app/build.gradle.kts`
(`sourceSets.main.res.srcDir`). It lives outside `res/` only because the
resource merger rejects non-resource files like this README inside `drawable/`.

## Source

- Repository: https://github.com/google/material-design-icons (Apache 2.0)
- Commit: `49d4db35df873165d6bd6ba09b063c7dafbac2f4`
  (committed 2026-10-09, "Update Symbols"; fetched 2026-10-21)
- Path pattern: `symbols/android/<name>/materialsymbolsoutlined/<file>`
- Style: Material Symbols Outlined, weight 400, grade 0, optical size 24,
  fill 0 unless the file name says `fill1`
- Browsable at https://fonts.google.com/icons?icon.set=Material+Symbols&icon.style=Outlined

| File                     | Upstream icon      | Replaces                                                       |
| ------------------------ | ------------------ | -------------------------------------------------------------- |
| `arrow_back_24px.xml`    | `arrow_back`       | `Icons.AutoMirrored.Filled.ArrowBack`                          |
| `close_24px.xml`         | `close`            | `Icons.Default.Clear`                                          |
| `search_24px.xml`        | `search`           | `Icons.Default.Search`                                         |
| `warning_fill1_24px.xml` | `warning` (filled) | `android.R.drawable.ic_dialog_alert` (notification small icon) |

## Local modification

Upstream files set `android:tint="?attr/colorControlNormal"`, an AppCompat
attribute this app doesn't define, so resource linking fails. That attribute
is stripped; Compose's `Icon()` applies its own tint anyway, and notification
small icons only use alpha.

## Updating

Run `./update.sh` to re-fetch everything at the pinned commit, or
`./update.sh <commit-ish>` (e.g. `master`) to pull a newer revision. Then
update the commit and date above. To add an icon, add a line to `ICONS` in
`update.sh` and a row to the table.
