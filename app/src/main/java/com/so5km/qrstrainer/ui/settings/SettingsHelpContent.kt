package com.so5km.qrstrainer.ui.settings

/**
 * Contains all the help content for individual settings
 */
object SettingsHelpContent {

    // === TRAINING SETTINGS ===
    
    val speedHelp = Pair(
        "Speed",
        "Controls the speed of the Morse code in Words Per Minute (WPM). Higher values mean faster code. " +
        "For beginners, start with 15-20 WPM. More experienced operators may prefer 25-30 WPM or higher."
    )
    
    val levelHelp = Pair(
        "Training Level",
        "Determines which characters are included in your training. Higher levels add more characters. " +
        "The Koch method starts with K and M, then gradually adds more characters as you improve."
    )
    
    val lockLevelHelp = Pair(
        "Lock Level",
        "When enabled, prevents automatic level progression. Use this if you want to practice at a specific level " +
        "without advancing, even if you meet the criteria to move up."
    )
    
    val sequenceLengthHelp = Pair(
        "Sequence Length",
        "Sets the minimum and maximum number of characters in each training sequence. " +
        "Shorter sequences are easier for beginners, while longer sequences help build copying endurance."
    )
    
    val repeatCountHelp = Pair(
        "Repeat Count",
        "How many times each sequence will be repeated before you need to answer. " +
        "More repeats make it easier to copy difficult characters."
    )
    
    val repeatSpacingHelp = Pair(
        "Repeat Spacing",
        "The delay between repetitions of the same sequence, in seconds. " +
        "Longer spacing helps train your short-term memory."
    )
    
    val timeoutHelp = Pair(
        "Answer Timeout",
        "How long (in seconds) you have to answer after the last repetition. " +
        "After this time, the sequence will be marked as incorrect."
    )
    
    val requiredCorrectHelp = Pair(
        "Required Correct",
        "How many correct answers you need to advance to the next level. " +
        "Higher values ensure you've truly mastered the current level before moving on."
    )
    
    val mistakesToDropHelp = Pair(
        "Mistakes to Drop Level",
        "How many consecutive mistakes will cause the app to drop you down a level. " +
        "This helps ensure you're training at an appropriate difficulty."
    )
    
    val sequenceDelayHelp = Pair(
        "Sequence Delay",
        "The delay between different training sequences, in seconds. " +
        "This gives you time to prepare for the next sequence."
    )
    
    val levelChangeDelayHelp = Pair(
        "Level Change Delay",
        "How long to wait (in seconds) after changing levels before starting new sequences. " +
        "This gives you time to notice and prepare for the new characters."
    )
    
    val farnsworthHelp = Pair(
        "Farnsworth Timing",
        "Increases spacing between characters while maintaining the character speed. " +
        "This makes copying easier while still training your brain to recognize fast characters."
    )
    
    // === SIGNAL SETTINGS ===
    
    val toneFrequencyHelp = Pair(
        "Tone Frequency",
        "The pitch of the Morse code tone in Hertz (Hz). Most operators prefer 500-700 Hz, " +
        "but you can adjust this to your preference or to match contest conditions."
    )
    
    val appVolumeHelp = Pair(
        "App Volume",
        "Controls the overall volume of the Morse code audio. " +
        "This is separate from your device's system volume."
    )
    
    val audioEnvelopeHelp = Pair(
        "Audio Envelope",
        "Controls how gradually the tone fades in and out. Higher values create smoother transitions " +
        "between elements, while lower values create more abrupt, key-click-like sounds."
    )
    
    val keyingStyleHelp = Pair(
        "Keying Style",
        "Simulates different types of Morse code transmission. Perfect timing is ideal for learning, " +
        "while other styles simulate hand-keying imperfections to help train for real-world conditions."
    )
    
    val wordSpacingHelp = Pair(
        "Word Spacing",
        "Controls the space between words in Morse code. Standard spacing is 7 units, " +
        "but you can adjust this to make copying easier or more challenging."
    )
    
    val groupSpacingHelp = Pair(
        "Group Spacing",
        "Controls the space between character groups. Increasing this can make it easier " +
        "to distinguish between groups, especially when learning."
    )
    
    // === FILTER SETTINGS ===
    
    val filterBandwidthHelp = Pair(
        "Filter Bandwidth",
        "Controls the width of the audio filter in Hz. Narrower filters (lower values) " +
        "help reject interference but can make the signal harder to copy."
    )
    
    val secondaryFilterBandwidthHelp = Pair(
        "Secondary Filter Bandwidth",
        "Controls the bandwidth of the secondary filter. This simulates having " +
        "multiple filter stages, which can improve selectivity but may introduce ringing."
    )
    
    val filterQHelp = Pair(
        "Filter Q Factor",
        "Controls the sharpness of the filter's cutoff. Higher values create steeper cutoffs " +
        "but can introduce more ringing in the audio."
    )
    
    val noiseVolumeHelp = Pair(
        "Noise Volume",
        "Controls the volume of simulated radio noise. Higher values make copying more challenging, " +
        "simulating poor band conditions."
    )
    
    val filterRingingHelp = Pair(
        "Filter Ringing",
        "When enabled, simulates the ringing effect of narrow CW filters. This helps train " +
        "your ear to copy through the artifacts created by very selective filters."
    )
    
    val primaryFilterOffsetHelp = Pair(
        "Primary Filter Offset",
        "Shifts the center frequency of the primary filter relative to the tone frequency. " +
        "This simulates mistuning and helps train your ear to copy off-frequency signals."
    )
    
    val secondaryFilterOffsetHelp = Pair(
        "Secondary Filter Offset",
        "Shifts the center frequency of the secondary filter. Using different offsets for " +
        "primary and secondary filters creates more complex filter responses."
    )
    
    // === NOISE SETTINGS ===
    
    val lfoFrequencyHelp = Pair(
        "LFO Frequency",
        "Controls the rate of simulated signal fading in Hz. Higher values create more rapid " +
        "fading, simulating challenging ionospheric conditions."
    )
    
    val continuousNoiseHelp = Pair(
        "Continuous Noise",
        "When enabled, the noise generator continues running even between sequences. " +
        "This creates a more realistic radio environment."
    )
} 