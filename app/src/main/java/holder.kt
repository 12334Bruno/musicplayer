/*
LazyColumn(
modifier = Modifier
.background(Color.Gray),

) {
    item {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(Color.DarkGray)
                .padding(16.dp)
        ) {
            Text(
                text = "Music App",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        }
    }
    stickyHeader {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .background(Color.DarkGray)
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Add your navigation items here
            Text(text = "Item 1", color = Color.White)
            Text(text = "Item 2", color = Color.White)
            Text(text = "Item 3", color = Color.White)
        }
    }

    items(50) { index ->
        Text(text = "Item $index", modifier = Modifier.fillMaxWidth().padding(16.dp))
    }
}

LaunchedEffect(key1 = true) {
    instance.stop()
}
*/
