BEGIN {}
/^[^\#]/ {	printf("%d ", $1-20)
	for (i=2; i<=NF; i++){
		printf("%s ",$i)
	}
	printf("\n")	
}
END{}
