BEGIN {}
/^[^\#]/ {	printf("%f ", $2)
	printf("qid:1 ")
	for (i=1; i<=NF-2; i++){
		printf("%d:%f ",i,$(i+2))
	}
	printf("#%s",$1)
	printf("\n")	
}
END{}
