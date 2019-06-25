# JUnit Platform Tester

This is a `Tester-Plugin` implementation that uses the JUnit Platform library \
	to discover and launch tests. It is mostly compatible with `biz.aQute.tester` \
	and (like `biz.aQute.tester`) adds itself to `-runbundles`, rather than \
	adding itself to `-runpath` (like `biz.aQute.junit`).