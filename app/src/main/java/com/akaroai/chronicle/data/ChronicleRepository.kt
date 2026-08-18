package com.akaroai.chronicle.data

import com.akaroai.chronicle.model.*
import kotlinx.coroutines.flow.Flow

class ChronicleRepository(private val dao:ChronicleDao){
    fun campaigns():Flow<List<CampaignEntity>> = dao.campaigns()
    fun archivedCampaigns():Flow<List<CampaignEntity>> = dao.archivedCampaigns()
    fun messages(id:Long)=dao.messages(id)
    fun memories(id:Long)=dao.memories(id)
    fun characters(id:Long)=dao.characters(id)
    suspend fun createCampaign(name:String,description:String="")=dao.insertCampaign(CampaignEntity(name=name.trim(),description=description.trim()))
    suspend fun updateCampaign(c:CampaignEntity)=dao.updateCampaign(c.copy(updatedAt=System.currentTimeMillis()))
    suspend fun archiveCampaign(c:CampaignEntity)=updateCampaign(c.copy(archived=true))
    suspend fun restoreCampaign(c:CampaignEntity)=updateCampaign(c.copy(archived=false))
    suspend fun deleteCampaign(c:CampaignEntity)=dao.deleteCampaign(c)
    suspend fun addMessage(id:Long,role:String,content:String){dao.insertMessage(MessageEntity(campaignId=id,role=role,content=content.trim()));dao.touchCampaign(id)}
    suspend fun addMemory(id:Long,title:String,content:String,category:String="Canon"){dao.insertMemory(MemoryEntity(campaignId=id,title=title.trim(),content=content.trim(),category=category.trim().ifBlank{"Canon"}));dao.touchCampaign(id)}
    suspend fun deleteMemory(m:MemoryEntity)=dao.deleteMemory(m)
    suspend fun addCharacter(c:CharacterEntity){dao.insertCharacter(c.copy(updatedAt=System.currentTimeMillis()));dao.touchCampaign(c.campaignId)}
    suspend fun updateCharacter(c:CharacterEntity){dao.updateCharacter(c.copy(updatedAt=System.currentTimeMillis()));dao.touchCampaign(c.campaignId)}
    suspend fun deleteCharacter(c:CharacterEntity)=dao.deleteCharacter(c)
}
