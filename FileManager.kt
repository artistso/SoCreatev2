package com.socreate.persistence

import android.content.Context
import com.socreate.model.Project
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class FileManager(private val context: Context) {

    fun saveProject(project: Project, fileName: String) {
        val file = File(context.filesDir, "$fileName.socreate")
        FileOutputStream(file).use { fos ->
            ProjectSerializer.save(project, fos)
        }
    }

    fun loadProject(fileName: String): Project? {
        val file = File(context.filesDir, "$fileName.socreate")
        if (!file.exists()) return null
        return FileInputStream(file).use { fis ->
            ProjectSerializer.load(fis)
        }
    }

    fun getAllProjects(): List<String> {
        return context.filesDir.listFiles { _, name -> name.endsWith(".socreate") }
            ?.map { it.nameWithoutExtension } ?: emptyList()
    }
}
