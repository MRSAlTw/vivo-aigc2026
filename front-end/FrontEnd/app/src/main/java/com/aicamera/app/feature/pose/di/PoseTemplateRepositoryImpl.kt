package com.aicamera.app.feature.pose.di

import com.aicamera.app.core.data.model.PoseCategory
import com.aicamera.app.core.data.model.PoseTemplate
import com.aicamera.app.core.data.repository.PoseTemplateRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class PoseTemplateRepositoryImpl : PoseTemplateRepository {

    private val mockTemplates = listOf(
        PoseTemplate(
            id = 1,
            name = "Standing Portrait",
            category = PoseCategory.PORTRAIT,
            thumbnailPath = "",
            keypoints = emptyMap()
        ),
        PoseTemplate(
            id = 2,
            name = "Outdoor Shot",
            category = PoseCategory.OUTDOOR,
            thumbnailPath = "",
            keypoints = emptyMap()
        ),
        PoseTemplate(
            id = 3,
            name = "Street Snap",
            category = PoseCategory.STREET_SNAP,
            thumbnailPath = "",
            keypoints = emptyMap()
        ),
    )

    override fun getAllTemplates(): Flow<List<PoseTemplate>> {
        return flowOf(mockTemplates)
    }

    override fun getByCategory(category: PoseCategory): Flow<List<PoseTemplate>> {
        return flowOf(mockTemplates.filter { it.category == category })
    }

    override suspend fun getById(id: Long): PoseTemplate? {
        return mockTemplates.find { it.id == id }
    }

    override suspend fun insert(template: PoseTemplate) {
        // Mock implementation - no-op
    }

    override suspend fun delete(template: PoseTemplate) {
        // Mock implementation - no-op
    }
}
