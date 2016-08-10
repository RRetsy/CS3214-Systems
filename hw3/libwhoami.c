#include <sys/types.h>
#include <pwd.h>
#include <unistd.h>

char* getunixname()
{
	struct passwd* result = getpwuid(getuid());
	return result->pw_name;
}

char* getrealname()
{
	struct passwd* result = getpwuid(getuid());
	return result->pw_gecos;
}
