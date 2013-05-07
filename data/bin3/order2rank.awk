# Input:arbitrary ascending retention time.
BEGIN {
	rank=1;
}

/^[^\#]/ {	printf("%d ", rank++)
	for (i=2; i<=NF; i++){
		printf("%s ",$i)
	}
	printf("\n")	
}
END{}
