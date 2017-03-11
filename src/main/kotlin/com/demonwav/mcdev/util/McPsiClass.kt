/*
 * Minecraft Dev for IntelliJ
 *
 * https://minecraftdev.org
 *
 * Copyright (c) 2017 minecraft-dev
 *
 * MIT License
 */

@file:JvmName("McPsiClass")
package com.demonwav.mcdev.util

import com.intellij.navigation.AnonymousElementProvider
import com.intellij.openapi.extensions.Extensions
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameterList
import com.intellij.psi.PsiPrimitiveType
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.annotations.Contract

// Type

@get:Contract(pure = true)
val PsiClassType.fullQualifiedName
    get() = resolve()!!.fullQualifiedName

// Class

@get:Contract(pure = true)
val PsiClass.outerQualifiedName
    get() = if (containingClass == null) qualifiedName else null

@get:Contract(pure = true)
val PsiClass.fullQualifiedName
    get() = outerQualifiedName ?: buildQualifiedName(StringBuilder()).toString()

fun PsiClass.appendFullQualifiedName(builder: StringBuilder): StringBuilder {
    return outerQualifiedName?.let { builder.append(it) } ?: buildQualifiedName(builder)
}

private fun PsiClass.buildQualifiedName(builder: StringBuilder): StringBuilder {
    buildInnerName(builder, PsiClass::outerQualifiedName)
    return builder
}

@get:Contract(pure = true)
private val PsiClass.outerShortName
    get() = if (containingClass == null) name else null

@get:Contract(pure = true)
val PsiClass.shortName: String
    get() {
        outerShortName?.let { return it }
        val builder = StringBuilder()
        buildInnerName(builder, PsiClass::outerShortName, '.')
        return builder.toString()
    }

inline fun PsiClass.buildInnerName(builder: StringBuilder, getName: (PsiClass) -> String?, separator: Char = '$') {
    var currentClass: PsiClass = this
    var parentClass: PsiClass?
    var name: String?
    val list = ArrayList<String>()

    do {
        parentClass = currentClass.containingClass
        if (parentClass != null) {
            // Add named inner class
            list.add(currentClass.name!!)
        } else {
            parentClass = currentClass.parent.findContainingClass()!!

            // Add index of anonymous class to list
            list.add(parentClass.getAnonymousIndex(currentClass).toString())
        }

        currentClass = parentClass
        name = getName(currentClass)
    } while (name == null)

    // Append name of outer class
    builder.append(name)

    // Append names for all inner classes
    for (i in list.lastIndex downTo 0) {
        builder.append(separator).append(list[i])
    }
}

@Contract(pure = true)
fun findQualifiedClass(fullQualifiedName: String, context: PsiElement): PsiClass? {
    return findQualifiedClass(context.project, fullQualifiedName, context.resolveScope)
}

@Contract(pure = true)
@JvmOverloads
fun findQualifiedClass(project: Project, fullQualifiedName: String,
                                scope: GlobalSearchScope = GlobalSearchScope.allScope(project)): PsiClass? {
    var innerPos = fullQualifiedName.indexOf('$')
    if (innerPos == -1) {
        return JavaPsiFacade.getInstance(project).findClass(fullQualifiedName, scope)
    }

    var currentClass = JavaPsiFacade.getInstance(project).findClass(fullQualifiedName.substring(0, innerPos), scope) ?: return null
    var outerPos: Int

    while (true) {
        outerPos = innerPos + 1
        innerPos = fullQualifiedName.indexOf('$', outerPos)

        if (innerPos == -1) {
            return currentClass.findInnerClass(fullQualifiedName.substring(outerPos))
        } else {
            currentClass = currentClass.findInnerClass(fullQualifiedName.substring(outerPos, innerPos)) ?: return null
        }
    }
}

private fun PsiClass.findInnerClass(name: String): PsiClass? {
    val anonymousIndex = name.toIntOrNull()
    return if (anonymousIndex == null) {
        // Named inner class
        findInnerClassByName(name, false)
    } else {
        val anonymousElements = anonymousElements ?: return null
        if (anonymousIndex <= anonymousElements.size) {
            anonymousElements[anonymousIndex - 1] as PsiClass
        } else {
            null
        }
    }
}

@Contract(pure = true)
fun PsiElement.getAnonymousIndex(anonymousElement: PsiElement): Int {
    // Attempt to find name for anonymous class
    for ((i, element) in anonymousElements!!.withIndex()) {
        if (manager.areElementsEquivalent(element, anonymousElement)) {
            return i + 1
        }
    }

    throw IllegalStateException("Failed to determine anonymous class for $anonymousElement")
}

@get:Contract(pure = true)
val PsiElement.anonymousElements: Array<PsiElement>?
    get() {
        for (provider in Extensions.getExtensions(AnonymousElementProvider.EP_NAME)) {
            val elements = provider.getAnonymousElements(this)
            if (elements.isNotEmpty()) {
                return elements
            }
        }

        return null
    }

// Inheritance

@Contract(pure = true)
fun PsiClass.extendsOrImplements(qualifiedClassName: String): Boolean {
    val aClass = JavaPsiFacade.getInstance(project).findClass(qualifiedClassName, resolveScope) ?: return false
    return manager.areElementsEquivalent(this, aClass) || this.isInheritor(aClass, true)
}

fun PsiClass.addImplements(qualifiedClassName: String) {
    val project = project
    val listenerClass = JavaPsiFacade.getInstance(project).findClass(qualifiedClassName, resolveScope) ?: return

    val elementFactory = JavaPsiFacade.getElementFactory(project)
    val element = elementFactory.createClassReferenceElement(listenerClass)

    val referenceList = implementsList
    if (referenceList != null) {
        referenceList.add(element)
    } else {
        add(elementFactory.createReferenceList(arrayOf(element)))
    }
}

// Member

fun PsiClass.findMatchingMethod(pattern: PsiMethod, checkBases: Boolean, name: String = pattern.name): PsiMethod? {
    return findMethodsByName(name, checkBases).firstOrNull { it.isMatchingMethod(pattern) }
}

fun PsiClass.findMatchingMethods(pattern: PsiMethod, checkBases: Boolean, name: String = pattern.name): List<PsiMethod> {
    return findMethodsByName(name, checkBases).filter { it.isMatchingMethod(pattern) }
}

inline fun PsiClass.findMatchingMethods(pattern: PsiMethod, checkBases: Boolean, name: String, func: (PsiMethod) -> Unit) {
    for (method in findMethodsByName(name, checkBases)) {
        if (method.isMatchingMethod(pattern)) {
            func(method)
        }
    }
}

fun PsiMethod.isMatchingMethod(pattern: PsiMethod): Boolean {
    return areReallyOnlyParametersErasureEqual(this.parameterList, pattern.parameterList)
        && this.returnType.isErasureEquivalentTo(pattern.returnType)
}

fun PsiClass.findMatchingField(pattern: PsiField, checkBases: Boolean, name: String = pattern.name!!): PsiField? {
    return findFieldByName(name, checkBases)?.takeIf { it.type.isErasureEquivalentTo(pattern.type) }
}

private fun areReallyOnlyParametersErasureEqual(parameterList1: PsiParameterList, parameterList2: PsiParameterList): Boolean {
    // Similar to MethodSignatureUtil.areParametersErasureEqual, but doesn't check method name
    if (parameterList1.parametersCount != parameterList2.parametersCount) {
        return false
    }

    val parameters1 = parameterList1.parameters
    val parameters2 = parameterList2.parameters
    for (i in parameters1.indices) {
        val type1 = parameters1[i].type
        val type2 = parameters2[i].type

        if (type1 is PsiPrimitiveType && (type2 !is PsiPrimitiveType || type1 != type2)) {
            return false
        }

        if (!type1.isErasureEquivalentTo(type2)) {
            return false
        }
    }

    return true
}
