lp->next = &x;
x.next = &z[9];

int i;
for(i = 8; i >= 0; i--)
{
	z[i+1].next = &z[i];
}

z[0].next = sp;
sp->next = &y;
