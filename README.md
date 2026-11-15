# Course Enrollment Management System (Java)

A desktop app for maintaining student course enrollment records. It's built with Java Swing (no external libraries) and stores data in a local JSON file. This is the Java counterpart of the Python/CustomTkinter version.

## Features

- **Enroll** a student with an ID, name, course, semester and study mode
- **Update** an existing enrollment
- **Delete** an enrollment, after asking for confirmation
- **Search** by Student ID (press Enter or click Search), or **Show all** records
- **Click a record** in the list to load it into the form for editing
- **Light and dark mode**: starts in the system theme on macOS, with a toggle in the header
- **Status bar** that shows a green message on success and a red one on error
- **Persistence**: every change is saved to `enrollments.json` right away

## Getting started

Requirements: JDK 11 or newer (the JDK, not just the JRE, because you need `javac`).

```
javac CourseEnrollmentSystem.java
java CourseEnrollmentSystem
```

Run both commands from the folder that contains `CourseEnrollmentSystem.java`. The filename must match the class name exactly.

`enrollments.json` is created in the folder you run the app from, the first time you save a record.

## Project structure

```
course-enrollment-java/
├── CourseEnrollmentSystem.java   # The whole application: UI, validation, persistence
├── README.md
├── .gitignore
└── enrollments.json              # Data file (created at runtime, git-ignored)
```

## How the code works

Everything lives in one class, `CourseEnrollmentSystem`, which extends `JFrame`.

### Configuration (top of the file)

| Constant | Purpose |
| --- | --- |
| `DATA_FILE` | Path to `enrollments.json`, in the current working directory |
| `COURSES` | Courses offered in the drop-down (B.Tech CSE/ECE/Mechanical/Civil, BCA, MCA, B.Sc Data Science, MBA) |
| `SEM_MIN`, `SEM_MAX` | Allowed semester range (1-8) |
| `BG`, `CARD`, `TEXT`, `MUTED`, ... | Colour pairs written as `{light, dark}`. The active theme picks index 0 or 1 |

To add a course or change the semester range, edit these constants. The UI picks up the change on its own.

### Data model

Records are kept in memory in a `TreeMap` keyed by Student ID (so they stay sorted), and saved to disk in this shape:

```
{
  "RA2511033010071": {
    "name": "Mohak Jain",
    "course": "B.Tech CSE",
    "semester": 3,
    "mode": "Regular"
  }
}
```

- `load()` reads the file. If it is missing or corrupt, the app starts empty.
- `save()` writes the whole map back, indented. It runs after every enroll, update and delete.
- A small built-in JSON reader (`Json` inner class) is used so no library is needed.

### Layout

```
┌────────────────────────────────────────────────────────┐
│ Header: title, subtitle, dark-mode switch              │
├──────────────────────┬─────────────────────────────────┤
│ Form card            │ Records card (stretches)        │
│  - Student ID        │  - record count                 │
│  - Student Name      │  - search box + Search/Show all │
│  - Course (menu)     │  - column header                │
│  - Semester          │  - scrollable list              │
│  - Mode (radio)      │                                 │
│  - Enroll / Update   │                                 │
│  - Delete / Clear    │                                 │
├──────────────────────┴─────────────────────────────────┤
│ Status bar                                             │
└────────────────────────────────────────────────────────┘
```

Each part is built by its own method: `buildHeader()`, `buildForm()`, `buildRecords()` and `buildStatus()`.

The record list is a `JList` with a monospaced font. `rowText()` cuts each field to a fixed width and pads it, so the columns line up. `applyTheme()` walks the component tree and recolours everything whenever the theme changes.

### Validation (`validate()`)

The form is checked before every enroll or update:

1. Student ID is required. It is stored in **upper case**.
2. Student Name is required and may contain only letters, spaces and `. ' -`. It is stored in **Title Case**.
3. Semester must be a whole number from `SEM_MIN` to `SEM_MAX`.

If a check fails, `fail()` shows an error dialog, turns the status bar red and returns `null`. If all checks pass, `validate()` returns the ID and the record.

### Operations

| Method | Button | Behaviour |
| --- | --- | --- |
| `doEnroll()` | Enroll | Validates the form and refuses an ID that already exists. Then saves, refreshes the list and clears the form |
| `doUpdate()` | Update | Validates the form and refuses an ID that doesn't exist. Then saves and refreshes the list |
| `doDelete()` | Delete | Reads the ID from the form, asks for confirmation, then removes the record and saves |
| `doSearch()` | Search / Enter | Shows only the matching record, selects it and fills the form |
| `showAll()` | Show all | Clears the search and lists every record, sorted by ID |
| `clearForm()` | Clear | Resets every field to its default |
| selection listener | (click a row) | Loads the selected record into the form |

`refreshList(ids)` redraws the list. It keeps `visibleIds` in the same order as the rows, so a click can be mapped back to a Student ID, even when a search has filtered the list.

## Limitations and ideas

- All data is in one local JSON file, so there is no multi-user access or concurrency control. SQLite or a database via JDBC would be the natural next step.
- You can't change a Student ID in place. Delete the record and enroll it again under the new ID.
- Search only matches the exact Student ID. Searching by name or course would be a useful addition.
- Dark mode is detected automatically on macOS only. On Windows and Linux, use the toggle.
- On macOS, buttons and drop-downs keep the system look, so they don't change colour in dark mode.
