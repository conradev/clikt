package com.github.ajalt.clikt.parameters.groups

import com.github.ajalt.clikt.completion.CompletionCandidates.Fixed
import com.github.ajalt.clikt.core.BadParameterValue
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.MissingParameter
import com.github.ajalt.clikt.parameters.internal.NullableLateinit
import com.github.ajalt.clikt.parameters.options.Option
import com.github.ajalt.clikt.parameters.options.RawOption
import com.github.ajalt.clikt.parsers.OptionParser
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

class ChoiceGroup<GroupT : OptionGroup, OutT>(
        internal val option: RawOption,
        internal val groups: Map<String, GroupT>,
        internal val transform: (GroupT?) -> OutT
) : ParameterGroupDelegate<OutT> {
    override val groupName: String? = null
    override val groupHelp: String? = null
    private var value: OutT by NullableLateinit("Cannot read from option delegate before parsing command line")
    private var chosenGroup: OptionGroup? = null

    override fun provideDelegate(thisRef: CliktCommand, prop: KProperty<*>): ReadOnlyProperty<CliktCommand, OutT> {
        option.provideDelegate(thisRef, prop) // infer the option name and register it
        thisRef.registerOptionGroup(this)
        for ((_, group) in groups) {
            for (option in group.options) {
                option.parameterGroup = this
                option.groupName = group.groupName
                thisRef.registerOption(option)
            }
        }
        return this
    }

    override fun getValue(thisRef: CliktCommand, property: KProperty<*>): OutT = value

    override fun finalize(context: Context, invocationsByOption: Map<Option, List<OptionParser.Invocation>>) {
        val key = option.value
        if (key == null) {
            value = transform(null)
            return
        }

        val group = groups[key]
                ?: throw BadParameterValue(
                        "invalid choice: $key. (choose from ${groups.keys.joinToString()})",
                        option,
                        context
                )
        group.finalize(context, invocationsByOption.filterKeys { it in group.options })
        chosenGroup = group
        value = transform(group)
    }

    override fun postValidate(context: Context) {
        chosenGroup?.options?.forEach { it.postValidate(context) }
    }
}

/**
 * Convert the option to an option group based on a fixed set of values.
 *
 * ### Example:
 *
 * ```kotlin
 * option().choice(mapOf("foo" to FooOptionGroup(), "bar" to BarOptionGroup()))
 * ```
 *
 * @see com.github.ajalt.clikt.parameters.types.choice
 */
fun <T : OptionGroup> RawOption.groupChoice(choices: Map<String, T>): ChoiceGroup<T, T?> {
    return ChoiceGroup(copy(completionCandidates = Fixed(choices.keys)), choices) { it }
}

/**
 * Convert the option to an option group based on a fixed set of values.
 *
 * ### Example:
 *
 * ```kotlin
 * option().choice("foo" to FooOptionGroup(), "bar" to BarOptionGroup())
 * ```
 *
 * @see com.github.ajalt.clikt.parameters.types.choice
 */
fun <T : OptionGroup> RawOption.groupChoice(vararg choices: Pair<String, T>): ChoiceGroup<T, T?> {
    return groupChoice(choices.toMap())
}

/**
 * If a [groupChoice] option is not called on the command line, throw a [MissingParameter] exception.
 *
 * ### Example:
 *
 * ```kotlin
 * option().choice("foo" to FooOptionGroup(), "bar" to BarOptionGroup()).required()
 * ```
 */
fun <T : OptionGroup> ChoiceGroup<T, T?>.required(): ChoiceGroup<T, T> {
    return ChoiceGroup(option, groups) { it ?: throw MissingParameter(option) }
}

