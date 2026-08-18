# Chronicle

Chronicle is a native Android campaign/story companion focused on long-term continuity.

## v0.1 foundation

This first build includes:

- Multiple completely separate campaigns
- Campaign tabs / fast switching
- Per-campaign chat history
- Per-campaign canon memory
- Per-campaign character records
- Local Room database storage
- A model/provider abstraction so Chronicle is not tied to one AI company
- A demo offline provider so the UI works before a remote/local AI provider is configured
- GitHub Actions APK build workflow

## Isolation rule

Every message, memory, and character row is keyed to a `campaignId`.
The database queries always filter by that ID. This is the foundation that prevents one campaign's lore from leaking into another.

## Planned next layers

- Provider Manager
  - OpenAI-compatible API endpoint
  - Local model / llama.cpp bridge
  - Per-campaign model choice
- Azriel GM/personality profile
- Automatic scene summaries
- Timeline and story arcs
- Quest / unresolved-thread tracker
- Relationship state
- Location and inventory state
- Memory inspector + continuity checker
- Full transcript import/export
- Campaign clone/archive
- Backups
- Character portraits / gallery
- Optional mature-roleplay profile depending on the selected provider's capabilities and rules

## Build

GitHub Actions is already included at:

`.github/workflows/build-apk.yml`

The generated debug APK will be uploaded as the artifact `Chronicle-APK`.
