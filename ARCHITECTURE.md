# Chronicle architecture

Chronicle owns the campaign state. The AI provider does not.

## Data boundary

Every persistent story object has a campaign ID:

Campaign
  -> Messages
  -> Memories
  -> Characters
  -> (future) Timeline
  -> (future) Quests
  -> (future) Relationships
  -> (future) Locations
  -> (future) Scene state

This guarantees independent campaign storage.

## AI provider boundary

`AiProvider` is an interface.

Chronicle prepares:
1. GM/personality system instructions
2. Campaign-specific memory
3. Recent conversation
4. Active scene state
5. User message

The provider returns only the next generated response.

This makes it possible to switch providers without losing the campaign.
