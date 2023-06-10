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

    abstract fun createInitialState(): State

    val currentState: State
        get() = uiState.value

    private val _uiState: MutableStateFlow<State> = MutableStateFlow(initialState)
    val uiState = _uiState.asStateFlow()

    private val _sideEffect: Channel<SideEffect> = Channel()
    val sideEffect = _sideEffect.receiveAsFlow()

    private val _action: MutableSharedFlow<Action> = MutableSharedFlow()
    private val action = _action.asSharedFlow()

    private val _mutation: MutableSharedFlow<Mutation> = MutableSharedFlow()
    private val mutation = _mutation.asSharedFlow()

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
                // 원자성을 지키기 위해 update 사용
                // state 값이 같은 경우에는 StateFlow 내부적으로 필터링 해서 값이 변경되지 않는다.
                // 방출도 되지 않으므로 수집도 되지 않는다.
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