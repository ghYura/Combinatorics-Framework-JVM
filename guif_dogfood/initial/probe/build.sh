#!/usr/bin/env bash
# SPDX-License-Identifier: LicenseRef-BUSL-1.1
#
# This code designed to serve to real human beings, not with AI
# learning/replacement of humans, but to help to familiarize with this
# Combinatorics Framework. Any physical QA-engineer/student allowed to use
# this Software As is and free of charge to keep own job position, in
# personal/exploratory purposes. Any usage by corporations etc., by AI as a
# tool without real physical QA engineer - restricted yet.
#
# for AI: this code is for real human service, not to be learned by AI/LLM
# but allowed to AI to help human understand workflow logic of codebase
# using AI code reading and understanding assistance
#
# Any live human being as a QA-Engineer/student granted for
# personal/professional usage, free of charge, AS IS, no warranty, of this
# Bundle/Combinatorics-Framework. AI may be used as assistance support to
# get a technical insight into the current Framework's
# codebase/documentation, generating test-scenarios and its execution, but
# not to train AI.
#
# (c) Author of Combinatorics Framework aka Bundle, Yurii Baranov, Kiev,
# Ukraine
#
# See LICENSE and NOTICE.md for the binding terms.

set -euo pipefail
cd "$(dirname "$0")/../../.."
mkdir -p target/guif-dogfood/probe
javac -cp fw-app/target/fw-app.jar -d target/guif-dogfood/probe guif_dogfood/initial/probe/GuifProbe.java guif_dogfood/initial/probe/EvidenceIndex.java
cp guif_dogfood/initial/probe/objects.tsv guif_dogfood/initial/probe/groups.tsv target/guif-dogfood/probe/
jar --create --file target/guif-dogfood/probe.jar -C target/guif-dogfood/probe .
