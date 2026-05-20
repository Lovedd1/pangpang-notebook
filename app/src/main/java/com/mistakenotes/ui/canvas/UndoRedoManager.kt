package com.mistakenotes.ui.canvas

class UndoRedoManager<T>(private val maxSize: Int = 50) {
    private val undoStack = mutableListOf<List<T>>()
    private val redoStack = mutableListOf<List<T>>()

    fun saveState(state: List<T>) {
        undoStack.add(state)
        if (undoStack.size > maxSize) {
            undoStack.removeAt(0)
        }
        redoStack.clear()
    }

    fun undo(currentState: List<T>): List<T>? {
        if (undoStack.isEmpty()) return null
        redoStack.add(currentState)
        return undoStack.removeAt(undoStack.lastIndex)
    }

    fun redo(currentState: List<T>): List<T>? {
        if (redoStack.isEmpty()) return null
        undoStack.add(currentState)
        return redoStack.removeAt(redoStack.lastIndex)
    }

    fun canUndo() = undoStack.isNotEmpty()
    fun canRedo() = redoStack.isNotEmpty()
}