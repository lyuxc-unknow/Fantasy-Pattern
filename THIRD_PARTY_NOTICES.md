# Third-Party Notices

## OmniSequence-Transfinite

The aggregated crafting-plan optimizer (`OmniMaxFastPlanner` and the AE2 tree
accessors it reads through) and the batch-dispatch integration (crafting CPU
extraction and push acceleration) in this project are adapted from
[OmniSequence-Transfinite](https://github.com/AyaYumi/OmniSequence-Transfinite),
licensed under the **MIT License**.

Upstream ships three licence files. `TEMPLATE_LICENSE.txt` covers only the
NeoForged MDK template files, which nothing here is derived from. `LICENSE`
carries the author alone (HibikiShino, the author's alias) and `LICENSE.txt`
carries the author together with the project's contributors. The adapted
sources are contributed work, so the notice reproduced below - and embedded in
the header of every adapted file - is `LICENSE.txt`, the broader of the two:

```
MIT License

Copyright (c) 2026 HibikiShino and OmniSequence: Transfinite contributors

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

This project is distributed under the same MIT License.

## AE2-VM

The optional AE2-VM crafting accelerator (`ae2vm` mod) may be bundled with or
loaded alongside this project. It is **not** part of this project's MIT
license; it is a modified fork of
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
To exercise your right to replace the LGPL component, either:

- drop a separately built `ae2vm` jar into your `mods` folder (it is declared
  as an optional dependency), or
- unpack this jar, replace the embedded AE2-VM jar, and repack it.

Modified AE2-VM source (LGPL-3.0) is available at the fork this project was
built with:
<https://github.com/lyuxc-unknow/AE2-VM-Fantasy-Pattern-Fork>

