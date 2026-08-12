# Third-Party Notices

## OmniSequence-Transfinite

[OmniSequence-Transfinite](https://github.com/AyaYumi/OmniSequence-Transfinite)
is an optional runtime dependency. When installed, it provides the aggregated
crafting planner, batch extraction, dispatch context, reusable-input plan, and
AE2 CPU accounting. This project contains only conditional compatibility Mixins
that admit fantasy patterns to those services; it does not include copied
OmniSequence planner or CPU sources. When OmniSequence is absent, AE2's normal
calculation and dispatch behavior is retained. OmniSequence-Transfinite is
licensed under the MIT License distributed with its own jar and source
repository.

## AE2-VM

The bundled AE2-VM component (`ae2vm` mod) is **not** part of this project's MIT
license. It owns durable-input compatibility for both the normal AE2 path and
the optional OmniSequence path. It is a modified fork of
[AE2-VM](https://github.com/TaoLe-si/AE2-VM), licensed under the
**GNU Lesser General Public License v3.0** (LGPL-3.0). The full license text
is provided in [`LICENSES/LGPL-3.0.txt`](LICENSES/LGPL-3.0.txt).

The local fork is based on upstream AE2-VM and adds, relative to upstream:

- Durable-input ownership: `DurableInputAdapters`, its deterministic-wear
  contract, AE2 input caches and CPU worn-variant extraction live entirely in
  AE2-VM instead of being split across this mod.
- Worn-variant accounting: the plan lists the actual durability variants
  present in the network inventory and only reports uncovered demand as
  missing.
- Diagnostics for the durable-extraction path.
- Build hygiene: removed author-local paths/proxy settings, published to the
  local Maven repository, AE2 resolved from the Modrinth Maven.

Under LGPL-3.0 §4, this combination is a "Combined Work": the AE2-VM library
keeps its LGPL-3.0 license, the rest of this project keeps its MIT license.
To exercise your right to replace the LGPL component, unpack this jar, replace
the embedded AE2-VM jar with a compatible build, and repack it.

Modified AE2-VM source (LGPL-3.0) is available at the fork this project was
built with:
<https://github.com/lyuxc-unknow/AE2-VM-Fantasy-Pattern-Fork>

