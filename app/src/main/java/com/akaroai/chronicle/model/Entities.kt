package com.akaroai.chronicle.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "campaigns")
data class CampaignEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String = "",
    val setting: String = "",
    val genreTone: String = "",
    val currentLocation: String = "",
    val currentObjective: String = "",
    val playerCharacterId: Long? = null,
    val archived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = CampaignEntity::class,
            parentColumns = ["id"],
            childColumns = ["campaignId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("campaignId")]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val campaignId: Long,
    val role: String,
    val content: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "memories",
    foreignKeys = [
        ForeignKey(
            entity = CampaignEntity::class,
            parentColumns = ["id"],
            childColumns = ["campaignId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("campaignId")]
)
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val campaignId: Long,
    val category: String = "Canon",
    val title: String,
    val content: String,
    val pinned: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "characters",
    foreignKeys = [
        ForeignKey(
            entity = CampaignEntity::class,
            parentColumns = ["id"],
            childColumns = ["campaignId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("campaignId"), Index("castTier")]
)
data class CharacterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val campaignId: Long,
    val name: String,
    val aliases: String = "",
    val species: String = "",
    val age: String = "",
    val pronouns: String = "",
    val appearance: String = "",
    val personality: String = "",
    val backstory: String = "",
    val abilities: String = "",
    val equipment: String = "",
    val relationship: String = "",
    val affiliations: String = "",
    val goals: String = "",
    val fears: String = "",
    val secrets: String = "",
    val injuries: String = "",
    val notes: String = "",
    val status: String = "Active",
    val castTier: String = "Supporting",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "change_proposals",
    foreignKeys = [
        ForeignKey(
            entity = CampaignEntity::class,
            parentColumns = ["id"],
            childColumns = ["campaignId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("campaignId"),
        Index("status"),
        Index("priority"),
        Index("groupType")
    ]
)
data class ChangeProposalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val campaignId: Long,
    val summary: String,
    val targetType: String,
    val targetId: Long? = null,
    val proposedChanges: String,
    val reason: String = "",
    val priority: String = "Normal",
    val groupType: String = "Other",
    val groupLabel: String = "",
    val status: String = "Pending",
    val supersededById: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)
