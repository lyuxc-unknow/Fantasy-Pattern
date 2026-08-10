package cn.lyxc.fantasytechnology.config;

/// How strictly the fantasy encoding terminal gates recipe transfer on owning the machine.
public enum DeviceAccessMode {

    /// Any recipe the terminal can represent may be transferred, exactly as before device access blocks existed.
    /// The blocks still work as storage; they simply stop being a requirement.
    UNRESTRICTED,

    /// A recipe may only be transferred when the network holds the devices it needs. What "needs" means is decided
    /// per recipe: a datapack rule if one covers it, otherwise four of the recipe category's own catalysts.
    REQUIRE_DEVICES
}
