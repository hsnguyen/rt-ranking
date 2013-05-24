#!/usr/bin/env Rscript

####################################
dcg <- function(ranklist, type, at){
	rel <- ranklist[,2]
	#DCG(p) = rel1 + SUM(rel_i/log2(i+1)){i=2...p}
	dcg <- 0
	if(type==1){
		dcg <- rel[1]
		for(i in 2:at){
			dcg <- dcg+rel[i]/log(i,base=2)
		}
	}
	#DCG(p)= SUM((2^rel_i-1)/log2(i+1)){i=1...p}
	if(type==2){
		for(i in 1:at){
			gain <- (2^rel[i]-1)/log(i+1,base=2)
			dcg <- dcg+gain
			#print(paste(i,":",rel[i],":",gain))
		}
	}
	return(dcg)
}
ndcg <- function(ranklist,decrease,type,at){
	sorted_list <- ranklist[order(ranklist[,1],decreasing=decrease),]
	ideal_list <- ranklist[order(ranklist[,2],decreasing=decrease),]
	dcg_at <- dcg(sorted_list,type,at)
	idcg_at <-dcg(ideal_list,type,at)
	
	#print(paste("======================",at," : ",dcg_at, ": ",idcg_at))
	return(dcg_at/idcg_at)
}

####################################

args<-commandArgs(trailingOnly=TRUE)
input<-args[1]

data<-read.csv(input,header=TRUE)
if(length(data)==1)
	data<-read.table(input,sep=":")
## res: res[,1]=predicted scores, res[,2]=actual relevant
res <- numeric()
if(length(data)==2)
	res <- cbind(data[,2],data[,1])
if(length(data)==5)
	res <- cbind(data[,1],data[,5])
#score_asc <- res[order(res[,1],decreasing=TRUE),]
#rank_asc <- res[order(res[,2],decreasing=TRUE),]
ndcg_list<-numeric()
for(i in 1:10){
	at <- as.integer(0.1*i*nrow(res))
	ndcg_list[i]<-ndcg(res,TRUE,1,at)
}
print(ndcg_list)
