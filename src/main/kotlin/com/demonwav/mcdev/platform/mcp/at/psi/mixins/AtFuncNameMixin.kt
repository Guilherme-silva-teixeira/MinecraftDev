/*
 * Minecraft Dev for IntelliJ
 *
 * https://minecraftdev.org
 *
 * Copyright (c) 2017 minecraft-dev
 *
 * MIT License
 */

package com.demonwav.mcdev.platform.mcp.at.psi.mixins

import com.intellij.psi.PsiElement

interface AtFuncNameMixin : PsiElement {

    val nameElement: PsiElement
    val funcNameText: String

    fun setFuncName(funcName: String)
}
