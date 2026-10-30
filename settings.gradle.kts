rootProject.name = "Ieum"

include(
	"services:gateway",
	"services:account",
	"services:family",
	"services:planner",
	"services:grocery",
	"services:expense",
	"services:notification",
	"libs:web",
	"libs:security",
	"libs:proto",
	"libs:grpc-client",
	"libs:grpc-server",
//	"libs:events",
)
