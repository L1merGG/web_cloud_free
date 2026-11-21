package com.limer.antigooglity.ui

import java.awt.Component
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.JFileChooser

object DirectoryPicker {
    fun pickDirectory(parent: Component? = null, initialPath: Path? = null): Path? {
        val chooser = JFileChooser().apply {
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            dialogTitle = "Выберите рабочую директорию (Workspace)"
            isAcceptAllFileFilterUsed = false
            if (initialPath != null && Files.isDirectory(initialPath)) {
                currentDirectory = initialPath.toFile()
            }
        }
        val result = chooser.showOpenDialog(parent)
        return if (result == JFileChooser.APPROVE_OPTION) {
            chooser.selectedFile?.toPath()
        } else {
            null
        }
    }
}
