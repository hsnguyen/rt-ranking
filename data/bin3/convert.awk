BEGIN {
	min=21;
}
/^[^\#]/ {	printf("%d ", ($1-min)/3+1)
	for (i=2; i<=NF; i++){
		printf("%s ",$i)
	}
	printf("\n")	
}
END{}
