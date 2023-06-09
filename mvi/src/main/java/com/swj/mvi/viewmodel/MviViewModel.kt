package com.swj.mvi.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

interface MviState

interface MviAction

interface MviMutation

interface MviSideEffect

abstract class MviViewModel<Action : MviAction, Mutation : MviMutation, State : MviState, SideEffect : MviSideEffect> : ViewModel()  {
    private val initialState: State by lazy { createInitialState() }

    val currentState: State
        get() = uiState.value

    private val _uiState: MutableStateFlow<State> = MutableStateFlow(initialState)
    private val uiState = _uiState.asStateFlow()

    private val _sideEffect: Channel<SideEffect> = Channel()
    val sideEffect = _sideEffect.receiveAsFlow()

    private val _action: MutableSharedFlow<Action> = MutableSharedFlow()
    private val action = _action.asSharedFlow()

    private val _mutation: MutableSharedFlow<Mutation> = MutableSharedFlow()
    private val mutation = _mutation.asSharedFlow()

    abstract fun createInitialState(): State
    abstract fun mutate(action: Action): Flow<Mutation>
    abstract fun reduce(state: State, mutation: Mutation) : State

    init {
        subscribeEvents()
    }

    private fun subscribeEvents() {

        viewModelScope.launch {
            action.collect {
                mutate(it).collect(
                    _mutation::emit
                )
            }
        }

        viewModelScope.launch {
            mutation.collect { mutation ->
                _uiState.update { currentState ->
                    reduce(currentState, mutation)
                }
            }
        }
    }

    final fun postAction(action: Action) {
        val newEvent = action
        viewModelScope.launch {
            _action.emit(newEvent)
        }
    }

    /**
     * Set new Effect
     */
    final fun postSideEffect(builder: () -> SideEffect) {
        val effectValue = builder()
        viewModelScope.launch { _sideEffect.send(effectValue) }
    }
}