BEGIN {
	newid=2;
}
/^[^\#]/ {
	for (i=1; i<=NF; i++){
		if(i==2)
			printf("qid:%d ",newid)
		else	
			printf("%s ",$i)
	}
	printf("\n")	
}
END{}
