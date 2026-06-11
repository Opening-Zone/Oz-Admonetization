package com.oz.android.utils.enums

/**
 * Enum defining ad states
 */
enum class AdState {
    IDLE,      // Default state, no action taken yet
    LOADING,   // Ad is loading from mediation
    LOADED,    // Ad loaded successfully, ready to show
    SHOWING    // Ad is currently showing
}