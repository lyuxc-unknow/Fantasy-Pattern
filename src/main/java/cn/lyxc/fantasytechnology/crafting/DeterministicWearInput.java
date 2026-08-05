package cn.lyxc.fantasytechnology.crafting;

import appeng.api.crafting.IPatternDetails;

/// Marks a pattern input whose {@link IPatternDetails.IInput#getRemainingKey} is a pure wear
/// function of the key alone: cheap to call, free of recipe or grid context, and returning
/// {@code null} exactly when one more use would destroy the item.
///
/// {@link MolecularReusableInputAdapters} needs that promise before it will follow a damaged tool's
/// wear chain and let the planner aggregate a whole job's worth of crafts into one step. AE2's own
/// crafting patterns cannot make it - their remainder comes out of running the recipe against a
/// rebuilt crafting grid, so walking a 1500-use tool means 1500 recipe evaluations, and AE2's
/// calculator deliberately plans such patterns one craft at a time instead. Inputs that do not
/// carry this marker keep that conservative treatment, which is what keeps jobs dispatched to
/// other mods' crafting devices planning exactly as they did before.
public interface DeterministicWearInput {
}
