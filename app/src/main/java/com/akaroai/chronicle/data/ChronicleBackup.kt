package com.akaroai.chronicle.data

import com.akaroai.chronicle.model.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class ChronicleBackupData(
    val campaign: CampaignEntity,
    val messages: List<MessageEntity>,
    val memories: List<MemoryEntity>,
    val characters: List<CharacterEntity>,
    val proposals: List<ChangeProposalEntity>,
    val locations: List<LocationEntity> = emptyList(),
    val factions: List<FactionEntity> = emptyList(),
    val quests: List<QuestEntity> = emptyList(),
    val timelineEvents: List<TimelineEventEntity> = emptyList()
)

object ChronicleBackup {
    const val FORMAT_NAME = "chronicle"
    const val FORMAT_VERSION = 4

    fun write(data: ChronicleBackupData, output: OutputStream) {
        ZipOutputStream(output.buffered()).use { zip ->
            zip.putJson(
                "manifest.json",
                JSONObject()
                    .put("format", FORMAT_NAME)
                    .put("formatVersion", FORMAT_VERSION)
                    .put("exportedAt", System.currentTimeMillis())
                    .put("campaignName", data.campaign.name)
                    .put("sourceCampaignId", data.campaign.id)
                    .put("characters", data.characters.size)
                    .put("memories", data.memories.size)
                    .put("messages", data.messages.size)
                    .put("proposals", data.proposals.size)
                    .put("locations", data.locations.size)
                    .put("factions", data.factions.size)
                    .put("quests", data.quests.size)
                    .put("timelineEvents", data.timelineEvents.size)
            )
            zip.putJson("campaign.json", campaignToJson(data.campaign))
            zip.putJson("characters.json", JSONArray().apply { data.characters.forEach { put(characterToJson(it)) } })
            zip.putJson("memories.json", JSONArray().apply { data.memories.forEach { put(memoryToJson(it)) } })
            zip.putJson("messages.json", JSONArray().apply { data.messages.forEach { put(messageToJson(it)) } })
            zip.putJson("proposals.json", JSONArray().apply { data.proposals.forEach { put(proposalToJson(it)) } })
            zip.putJson("locations.json", JSONArray().apply { data.locations.forEach { put(locationToJson(it)) } })
            zip.putJson("factions.json", JSONArray().apply { data.factions.forEach { put(factionToJson(it)) } })
            zip.putJson("quests.json", JSONArray().apply { data.quests.forEach { put(questToJson(it)) } })
            zip.putJson("timeline.json", JSONArray().apply { data.timelineEvents.forEach { put(timelineToJson(it)) } })
        }
    }

    fun read(input: InputStream): ChronicleBackupData {
        val entries = mutableMapOf<String, String>()
        ZipInputStream(input.buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val out = ByteArrayOutputStream()
                    zip.copyTo(out)
                    entries[entry.name] = out.toString(StandardCharsets.UTF_8.name())
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        val manifest = JSONObject(entries["manifest.json"]
            ?: error("Not a Chronicle backup: manifest.json is missing."))
        if (manifest.optString("format") != FORMAT_NAME) {
            error("This file is not a Chronicle campaign backup.")
        }
        val version = manifest.optInt("formatVersion", 0)
        if (version <= 0 || version > FORMAT_VERSION) {
            error("This Chronicle backup format is newer than this app can read.")
        }

        return ChronicleBackupData(
            campaign = campaignFromJson(JSONObject(entries["campaign.json"]
                ?: error("Campaign data is missing from this backup."))),
            characters = parseArray(entries["characters.json"]) { characterFromJson(it) },
            memories = parseArray(entries["memories.json"]) { memoryFromJson(it) },
            messages = parseArray(entries["messages.json"]) { messageFromJson(it) },
            proposals = parseArray(entries["proposals.json"]) { proposalFromJson(it) },
            locations = parseArray(entries["locations.json"]) { locationFromJson(it) },
            factions = parseArray(entries["factions.json"]) { factionFromJson(it) },
            quests = parseArray(entries["quests.json"]) { questFromJson(it) },
            timelineEvents = parseArray(entries["timeline.json"]) { timelineFromJson(it) }
        )
    }

    private fun ZipOutputStream.putJson(name: String, value: Any) {
        putNextEntry(ZipEntry(name))
        write(value.toString().toByteArray(StandardCharsets.UTF_8))
        closeEntry()
    }

    private fun <T> parseArray(raw: String?, mapper: (JSONObject) -> T): List<T> {
        if (raw == null) return emptyList()
        val a = JSONArray(raw)
        return buildList {
            for (i in 0 until a.length()) add(mapper(a.getJSONObject(i)))
        }
    }

    private fun campaignToJson(c: CampaignEntity) = JSONObject()
        .put("id", c.id).put("name", c.name).put("description", c.description)
        .put("setting", c.setting).put("genreTone", c.genreTone)
        .put("currentLocation", c.currentLocation).put("currentObjective", c.currentObjective)
        .put("playerCharacterId", c.playerCharacterId ?: JSONObject.NULL)
        .put("archived", c.archived).put("createdAt", c.createdAt).put("updatedAt", c.updatedAt)

    private fun campaignFromJson(o: JSONObject) = CampaignEntity(
        id = o.optLong("id"),
        name = o.optString("name", "Imported campaign"),
        description = o.optString("description"),
        setting = o.optString("setting"),
        genreTone = o.optString("genreTone"),
        currentLocation = o.optString("currentLocation"),
        currentObjective = o.optString("currentObjective"),
        playerCharacterId = nullableLong(o, "playerCharacterId"),
        archived = o.optBoolean("archived", false),
        createdAt = o.optLong("createdAt", System.currentTimeMillis()),
        updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
    )

    private fun characterToJson(c: CharacterEntity) = JSONObject()
        .put("id", c.id).put("campaignId", c.campaignId).put("name", c.name)
        .put("aliases", c.aliases).put("species", c.species).put("age", c.age)
        .put("pronouns", c.pronouns).put("appearance", c.appearance)
        .put("personality", c.personality).put("backstory", c.backstory)
        .put("abilities", c.abilities).put("equipment", c.equipment)
        .put("relationship", c.relationship).put("affiliations", c.affiliations)
        .put("goals", c.goals).put("fears", c.fears).put("secrets", c.secrets)
        .put("injuries", c.injuries).put("notes", c.notes)
        .put("currentLocation", c.currentLocation)
        .put("status", c.status).put("castTier", c.castTier)
        .put("integrityMode", c.integrityMode)
        .put("protectedFields", c.protectedFields)
        .put("createdAt", c.createdAt).put("updatedAt", c.updatedAt)

    private fun characterFromJson(o: JSONObject) = CharacterEntity(
        id = o.optLong("id"), campaignId = o.optLong("campaignId"),
        name = o.optString("name"), aliases = o.optString("aliases"),
        species = o.optString("species"), age = o.optString("age"),
        pronouns = o.optString("pronouns"), appearance = o.optString("appearance"),
        personality = o.optString("personality"), backstory = o.optString("backstory"),
        abilities = o.optString("abilities"), equipment = o.optString("equipment"),
        relationship = o.optString("relationship"), affiliations = o.optString("affiliations"),
        goals = o.optString("goals"), fears = o.optString("fears"),
        secrets = o.optString("secrets"), injuries = o.optString("injuries"),
        notes = o.optString("notes"),
        currentLocation = o.optString("currentLocation").ifBlank {
            Regex("""(?i)Currently at\s+([^.\n]+)\.""")
                .findAll(o.optString("notes"))
                .lastOrNull()
                ?.groupValues?.getOrNull(1)?.trim().orEmpty()
        },
        status = o.optString("status", "Active"),
        castTier = o.optString("castTier", "Supporting"),
        integrityMode = o.optString("integrityMode", "Balanced"),
        protectedFields = o.optString("protectedFields"),
        createdAt = o.optLong("createdAt", System.currentTimeMillis()),
        updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
    )

    private fun memoryToJson(m: MemoryEntity) = JSONObject()
        .put("id", m.id).put("campaignId", m.campaignId).put("category", m.category)
        .put("title", m.title).put("content", m.content).put("pinned", m.pinned)
        .put("createdAt", m.createdAt)

    private fun memoryFromJson(o: JSONObject) = MemoryEntity(
        id = o.optLong("id"), campaignId = o.optLong("campaignId"),
        category = o.optString("category", "Canon"), title = o.optString("title"),
        content = o.optString("content"), pinned = o.optBoolean("pinned", true),
        createdAt = o.optLong("createdAt", System.currentTimeMillis())
    )

    private fun messageToJson(m: MessageEntity) = JSONObject()
        .put("id", m.id).put("campaignId", m.campaignId).put("role", m.role)
        .put("content", m.content).put("createdAt", m.createdAt)

    private fun messageFromJson(o: JSONObject) = MessageEntity(
        id = o.optLong("id"), campaignId = o.optLong("campaignId"),
        role = o.optString("role"), content = o.optString("content"),
        createdAt = o.optLong("createdAt", System.currentTimeMillis())
    )

    private fun proposalToJson(p: ChangeProposalEntity) = JSONObject()
        .put("id", p.id).put("campaignId", p.campaignId).put("summary", p.summary)
        .put("targetType", p.targetType).put("targetId", p.targetId ?: JSONObject.NULL)
        .put("proposedChanges", p.proposedChanges).put("reason", p.reason)
        .put("priority", p.priority).put("groupType", p.groupType)
        .put("groupLabel", p.groupLabel).put("changeMode", p.changeMode)
        .put("evidenceType", p.evidenceType).put("integrityWarning", p.integrityWarning)
        .put("status", p.status).put("supersededById", p.supersededById ?: JSONObject.NULL)
        .put("createdAt", p.createdAt)

    private fun proposalFromJson(o: JSONObject) = ChangeProposalEntity(
        id = o.optLong("id"), campaignId = o.optLong("campaignId"),
        summary = o.optString("summary"), targetType = o.optString("targetType"),
        targetId = nullableLong(o, "targetId"), proposedChanges = o.optString("proposedChanges"),
        reason = o.optString("reason"), priority = o.optString("priority", "Normal"),
        groupType = o.optString("groupType", "Other"), groupLabel = o.optString("groupLabel"),
        changeMode = o.optString("changeMode", "Replace"),
        evidenceType = o.optString("evidenceType", "Unverified"),
        integrityWarning = o.optString("integrityWarning"),
        status = o.optString("status", "Pending"), supersededById = nullableLong(o, "supersededById"),
        createdAt = o.optLong("createdAt", System.currentTimeMillis())
    )

    private fun locationToJson(x: LocationEntity) = JSONObject()
        .put("name", x.name).put("region", x.region).put("parentLocation", x.parentLocation)
        .put("description", x.description).put("discoveryState", x.discoveryState)
        .put("status", x.status).put("notes", x.notes).put("firstSeenAt", x.firstSeenAt).put("updatedAt", x.updatedAt)
    private fun locationFromJson(o: JSONObject) = LocationEntity(
        campaignId = 0, name = o.optString("name"), region = o.optString("region"),
        parentLocation = o.optString("parentLocation"), description = o.optString("description"),
        discoveryState = o.optString("discoveryState","Discovered"), status = o.optString("status","Active"),
        notes = o.optString("notes"), firstSeenAt = o.optLong("firstSeenAt",System.currentTimeMillis()),
        updatedAt = o.optLong("updatedAt",System.currentTimeMillis())
    )
    private fun factionToJson(x: FactionEntity) = JSONObject()
        .put("name",x.name).put("description",x.description).put("alignment",x.alignment)
        .put("relationshipToParty",x.relationshipToParty).put("status",x.status).put("goals",x.goals)
        .put("notes",x.notes).put("createdAt",x.createdAt).put("updatedAt",x.updatedAt)
    private fun factionFromJson(o: JSONObject) = FactionEntity(
        campaignId=0,name=o.optString("name"),description=o.optString("description"),
        alignment=o.optString("alignment"),relationshipToParty=o.optString("relationshipToParty","Unknown"),
        status=o.optString("status","Active"),goals=o.optString("goals"),notes=o.optString("notes"),
        createdAt=o.optLong("createdAt",System.currentTimeMillis()),updatedAt=o.optLong("updatedAt",System.currentTimeMillis())
    )
    private fun questToJson(x: QuestEntity) = JSONObject()
        .put("title",x.title).put("summary",x.summary).put("status",x.status).put("objective",x.objective)
        .put("relatedLocation",x.relatedLocation).put("relatedFaction",x.relatedFaction)
        .put("importance",x.importance).put("notes",x.notes).put("createdAt",x.createdAt).put("updatedAt",x.updatedAt)
    private fun questFromJson(o: JSONObject) = QuestEntity(
        campaignId=0,title=o.optString("title"),summary=o.optString("summary"),status=o.optString("status","Active"),
        objective=o.optString("objective"),relatedLocation=o.optString("relatedLocation"),relatedFaction=o.optString("relatedFaction"),
        importance=o.optString("importance","Normal"),notes=o.optString("notes"),
        createdAt=o.optLong("createdAt",System.currentTimeMillis()),updatedAt=o.optLong("updatedAt",System.currentTimeMillis())
    )
    private fun timelineToJson(x: TimelineEventEntity) = JSONObject()
        .put("title",x.title).put("summary",x.summary).put("eventType",x.eventType).put("location",x.location)
        .put("involvedCharacters",x.involvedCharacters).put("storyArc",x.storyArc).put("importance",x.importance)
        .put("source",x.source).put("storyOrder",x.storyOrder).put("createdAt",x.createdAt)
    private fun timelineFromJson(o: JSONObject) = TimelineEventEntity(
        campaignId=0,title=o.optString("title"),summary=o.optString("summary"),eventType=o.optString("eventType","Event"),
        location=o.optString("location"),involvedCharacters=o.optString("involvedCharacters"),storyArc=o.optString("storyArc"),
        importance=o.optString("importance","Normal"),source=o.optString("source","Review"),storyOrder=o.optLong("storyOrder"),
        createdAt=o.optLong("createdAt",System.currentTimeMillis())
    )

    private fun nullableLong(o: JSONObject, key: String): Long? =
        if (!o.has(key) || o.isNull(key)) null else o.optLong(key)
}
