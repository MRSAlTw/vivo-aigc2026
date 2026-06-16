package com.aicamera.app.core.data.repository

import com.aicamera.app.core.data.model.PoseCategory
import com.aicamera.app.core.data.model.PoseTemplate
import kotlinx.coroutines.flow.Flow

/**
 * Pose template Repository interface
 * Isolates Room implementation, ViewModel layer depends on this interface rather than Room DAO
 */
interface PoseTemplateRepository {
    fun getAllTemplates(): Flow<List<PoseTemplate>>
    fun getByCategory(category: PoseCategory): Flow<List<PoseTemplate>>
    suspend fun getById(id: Long): PoseTemplate?
    suspend fun insert(template: PoseTemplate)
    suspend fun delete(template: PoseTemplate)
}
