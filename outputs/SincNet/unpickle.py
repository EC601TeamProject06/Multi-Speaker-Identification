import torch
import json  

with open('model_raw.pkl', 'rb') as f:
    data = torch.load(f)
    print(data)
     
torch.save(str(data),'model.txt')