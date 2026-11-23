package com.kalaiselvan.ride.bluetooth

import java.util.UUID

object BleUuids {
    // UUIDs required to communicate with Kawasaki motorcycles (kept unchanged for interoperability)
    val SERVICE_UUID: UUID = UUID.fromString("92faec07-c075-4b7c-a6c2-bbd1d1a150f5")
    val CONTROL_UUID: UUID = UUID.fromString("acf1b15c-10f9-4942-a32d-f9e019b95402")
    val NOTIFY_UUIDS = listOf(
        UUID.fromString("3aabbb34-eac0-40f5-9d50-3a1ee6787136"),
        UUID.fromString("02fad1bd-358e-441c-b296-fe874af38a7e"),
        UUID.fromString("5e119eba-35a7-4463-a7af-7fa40a302350")
    )
}

